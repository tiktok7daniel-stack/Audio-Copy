"""
Builds "cloned Piper voice" templates: one ONNX that takes the same inputs as a Piper model
(input, input_lengths, scales[, sid]) and outputs the speech already re-voiced by OpenVoice.

  Piper VITS -> waveform -> STFT (as ONNX ops) -> OpenVoice tone_color(src=base voice, dest=CLONED) -> output

The cloned voice's 256-float embedding is a constant filled with a sentinel pattern. The phone
replaces those 1024 bytes with the real embedding (offset written to templates.json), so any app
that plays Piper voices (sherpa-onnx) speaks in the cloned voice.
"""
import json, os, struct, subprocess, sys, tarfile, urllib.request
import numpy as np
import onnx
from onnx import TensorProto, helper, numpy_helper, version_converter
from huggingface_hub import hf_hub_download

sys.path.insert(0, os.path.dirname(__file__))
from test_openvoice import spec, load, SR

REL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"
VOICES = {
    "ar": ("vits-piper-ar_JO-kareem-medium-int8", ["مرحبا، كيف حالك اليوم؟", "هذه قصة طويلة عن البطل في المدينة.", "قال الرجل إنه سيعود غدا."]),
    "en": ("vits-piper-en_US-lessac-medium-int8", ["Hello, how are you today?", "This is a long story about the hero of the city.", "He said he would come back tomorrow."]),
}
NFFT, HOP, EMB = 1024, 256, 256
SENTINEL = np.array([123456.0 + i for i in range(EMB)], dtype=np.float32)  # exact in float32
OUT = "templates"
os.makedirs(OUT, exist_ok=True)

import onnxruntime as ort
import sherpa_onnx

tc_path = hf_hub_download("eugenehp/openvoice", "tone_color.onnx")
ext = ort.InferenceSession(hf_hub_download("eugenehp/openvoice", "tone_extract.onnx"))


def embed(y):
    return ext.run(None, {"input": np.ascontiguousarray(spec(y)[None].transpose(0, 2, 1))})[0].reshape(-1)[:EMB]


def opset(m):
    return max(o.version for o in m.opset_import if o.domain in ("", "ai.onnx"))


def piper_tts(model, d):
    return sherpa_onnx.OfflineTts(sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(
        vits=sherpa_onnx.OfflineTtsVitsModelConfig(model=model, tokens=f"{d}/tokens.txt",
                                                  data_dir=os.path.abspath(f"{d}/espeak-ng-data")), num_threads=2)))


def build(lang):
    name, sentences = VOICES[lang]
    urllib.request.urlretrieve(f"{REL}/{name}.tar.bz2", f"{name}.tar.bz2")
    tarfile.open(f"{name}.tar.bz2").extractall(".")
    piper_file = [f for f in os.listdir(name) if f.endswith(".onnx")][0]
    P = onnx.load(f"{name}/{piper_file}")
    C = onnx.load(tc_path)
    print(lang, "piper opset", opset(P), "ir", P.ir_version, "| tone_color opset", opset(C), "ir", C.ir_version)
    print("  piper inputs", [(i.name, [d.dim_param or d.dim_value for d in i.type.tensor_type.shape.dim]) for i in P.graph.input])
    print("  piper outputs", [(o.name, [d.dim_param or d.dim_value for d in o.type.tensor_type.shape.dim]) for o in P.graph.output])
    print("  piper metadata", {p.key: p.value for p in P.metadata_props})

    # Source timbre = the base Piper voice itself, averaged over a few sentences
    base_tts = piper_tts(f"{name}/{piper_file}", name)
    src = np.mean([embed(np.array(base_tts.generate(t, sid=0, speed=1.0).samples, dtype=np.float32)) for t in sentences], axis=0).astype(np.float32)

    target = max(opset(P), opset(C))
    if opset(P) != target:
        P = version_converter.convert_version(P, target)
    if opset(C) != target:
        C = version_converter.convert_version(C, target)
    C = onnx.compose.add_prefix(C, "tc_")

    p_out = P.graph.output[0].name
    for n in P.graph.node:
        for i, o in enumerate(n.output):
            if o == p_out:
                n.output[i] = "piper_wave"

    # STFT as a convolution with a windowed DFT basis (matches Spectrogram.compute / test_openvoice.spec)
    win = (0.5 - 0.5 * np.cos(2 * np.pi * np.arange(NFFT) / NFFT)).astype(np.float64)
    k = np.arange(NFFT // 2 + 1)[:, None]
    n = np.arange(NFFT)[None, :]
    basis = np.concatenate([np.cos(2 * np.pi * k * n / NFFT) * win, -np.sin(2 * np.pi * k * n / NFFT) * win]).astype(np.float32)
    inits = [
        numpy_helper.from_array(basis[:, None, :], "stft_basis"),
        numpy_helper.from_array(np.array([1, 1, -1], dtype=np.int64), "stft_shape"),
        numpy_helper.from_array(np.array([0, 0, (NFFT - HOP) // 2, 0, 0, (NFFT - HOP) // 2], dtype=np.int64), "stft_pads"),
        numpy_helper.from_array(np.array([0], dtype=np.int64), "s0"),
        numpy_helper.from_array(np.array([513], dtype=np.int64), "s513"),
        numpy_helper.from_array(np.array([1026], dtype=np.int64), "s1026"),
        numpy_helper.from_array(np.array([1], dtype=np.int64), "ax1"),
        numpy_helper.from_array(np.array([2], dtype=np.int64), "s2"),
        numpy_helper.from_array(np.array([3], dtype=np.int64), "s3"),
        numpy_helper.from_array(np.array([0], dtype=np.int64), "ax0"),
        numpy_helper.from_array(np.array(1e-6, dtype=np.float32), "stft_eps"),
        numpy_helper.from_array(src.reshape(1, EMB, 1), "tc_src_tone"),
        numpy_helper.from_array(SENTINEL.reshape(1, EMB, 1), "tc_dest_tone"),
        numpy_helper.from_array(np.array([0.3], dtype=np.float32), "tc_tau"),
    ]
    nodes = [
        helper.make_node("Reshape", ["piper_wave", "stft_shape"], ["stft_x"]),
        helper.make_node("Pad", ["stft_x", "stft_pads"], ["stft_p"], mode="reflect"),
        helper.make_node("Conv", ["stft_p", "stft_basis"], ["stft_c"], strides=[HOP], kernel_shape=[NFFT]),
        helper.make_node("Slice", ["stft_c", "s0", "s513", "ax1"], ["stft_re"]),
        helper.make_node("Slice", ["stft_c", "s513", "s1026", "ax1"], ["stft_im"]),
        helper.make_node("Mul", ["stft_re", "stft_re"], ["stft_re2"]),
        helper.make_node("Mul", ["stft_im", "stft_im"], ["stft_im2"]),
        helper.make_node("Add", ["stft_re2", "stft_im2"], ["stft_pow"]),
        helper.make_node("Add", ["stft_pow", "stft_eps"], ["stft_pow_e"]),
        helper.make_node("Sqrt", ["stft_pow_e"], ["tc_audio"]),
        helper.make_node("Shape", ["tc_audio"], ["stft_dims"]),
        helper.make_node("Slice", ["stft_dims", "s2", "s3", "ax0"], ["tc_audio_length"]),
    ]
    # the five real tone_color inputs are now produced by the STFT nodes / constants above
    tc_inputs = {"tc_audio", "tc_audio_length", "tc_src_tone", "tc_dest_tone", "tc_tau"}
    graph = helper.make_graph(
        list(P.graph.node) + nodes + list(C.graph.node) + [helper.make_node("Identity", [C.graph.output[0].name], [p_out])],
        f"voxgate_clone_{lang}",
        list(P.graph.input),
        [helper.make_tensor_value_info(p_out, TensorProto.FLOAT, [1, 1, None])],
        initializer=list(P.graph.initializer) + [i for i in C.graph.initializer if i.name not in tc_inputs] + inits,
    )
    domains = {}
    for m in (P, C):
        for o in m.opset_import:
            domains[o.domain] = max(domains.get(o.domain, 0), o.version)
    model = helper.make_model(graph, opset_imports=[helper.make_opsetid(d, v) for d, v in domains.items()],
                              producer_name="voxgate")
    model.ir_version = max(P.ir_version, C.ir_version)
    for p in P.metadata_props:
        model.metadata_props.append(p)
    model.metadata_props.append(onnx.StringStringEntryProto(key="voxgate", value="cloned piper voice"))
    onnx.checker.check_model(model)
    path = f"{OUT}/voxgate-clone-{lang}.onnx"
    onnx.save(model, path)

    raw = open(path, "rb").read()
    sent = SENTINEL.astype("<f4").tobytes()
    offset = raw.find(sent)
    assert offset > 0 and raw.find(sent, offset + 1) < 0, "sentinel must appear exactly once"
    print(f"  template {path}: {len(raw) // 1_000_000} MB, embedding at byte {offset}")
    return name, piper_file, path, offset


def patch(path, offset, emb, dst):
    raw = bytearray(open(path, "rb").read())
    raw[offset:offset + EMB * 4] = emb.astype("<f4").tobytes()
    open(dst, "wb").write(raw)


def cos(a, b):
    return float(np.dot(a, b) / (np.linalg.norm(a) * np.linalg.norm(b)))


subprocess.run(["espeak-ng", "-v", "en-us+f3", "-p", "75", "-w", "ref_en.wav",
                "This is my English reference voice, the one I want to hear reading my novels."], check=True)
ref_emb = embed(load("ref_en.wav"))

manifest = {}
for lang in ("ar", "en"):
    name, piper_file, path, offset = build(lang)
    manifest[lang] = {"file": os.path.basename(path), "offset": offset, "size": os.path.getsize(path), "base": name}
    # Simulate the phone: patch in a real embedding, then load it exactly like a reader app loads a Piper voice
    patch(path, offset, ref_emb, "patched.onnx")
    text = VOICES[lang][1][1]
    cloned = np.array(piper_tts("patched.onnx", name).generate(text, sid=0, speed=1.0).samples, dtype=np.float32)
    plain = np.array(piper_tts(f"{name}/{piper_file}", name).generate(text, sid=0, speed=1.0).samples, dtype=np.float32)
    c_ref, p_ref = cos(embed(cloned), ref_emb), cos(embed(plain), ref_emb)
    print(f"  {lang}: cloned {len(cloned)} samples peak {np.abs(cloned).max():.3f}; "
          f"timbre similarity to reference: cloned {c_ref:.3f} vs plain base {p_ref:.3f}")
    assert len(cloned) > 10000 and np.isfinite(cloned).all() and np.abs(cloned).max() > 0.01
    assert c_ref > p_ref, "the cloned voice must sound closer to the reference than the base voice"

json.dump(manifest, open(f"{OUT}/templates.json", "w"), indent=1)
print(json.dumps(manifest, indent=1))
print("TEMPLATES OK")
