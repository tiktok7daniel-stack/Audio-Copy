"""Desktop check of the OpenVoice V2 ONNX tone-color pipeline that the Android app re-implements in Kotlin."""
import json, subprocess, numpy as np
from scipy.io import wavfile
from scipy.signal import resample_poly
from huggingface_hub import hf_hub_download
import onnxruntime as ort

REPO = "eugenehp/openvoice"
for f in ("tone_config.json", "manifest.json"):
    print(f, open(hf_hub_download(REPO, f)).read()[:1500])
ext = ort.InferenceSession(hf_hub_download(REPO, "tone_extract.onnx"), providers=["CPUExecutionProvider"])
cvt = ort.InferenceSession(hf_hub_download(REPO, "tone_color.onnx"), providers=["CPUExecutionProvider"])
for s in (ext, cvt):
    print([(i.name, i.shape, i.type) for i in s.get_inputs()], [(o.name, o.shape, o.type) for o in s.get_outputs()])

SR, NFFT, HOP = 22050, 1024, 256
win = np.hanning(NFFT + 1)[:-1].astype(np.float32)  # periodic hann like torch.hann_window


def spec(y):
    pad = (NFFT - HOP) // 2
    y = np.pad(y, (pad, pad), mode="reflect")
    n = 1 + (len(y) - NFFT) // HOP
    frames = np.stack([y[i * HOP:i * HOP + NFFT] * win for i in range(n)])
    s = np.fft.rfft(frames, axis=1)
    return np.sqrt(s.real ** 2 + s.imag ** 2 + 1e-6).T.astype(np.float32)  # [513, T]


def load(path):
    sr, y = wavfile.read(path)
    y = y.astype(np.float32) / 32768.0
    if sr != SR:
        g = np.gcd(sr, SR)
        y = resample_poly(y, SR // g, sr // g).astype(np.float32)
    return y


def embed(y):
    s = spec(y)[None]                      # [1,513,T]
    return ext.run(None, {"input": np.ascontiguousarray(s.transpose(0, 2, 1))})[0].reshape(1, 256, 1)


subprocess.run(["espeak-ng", "-v", "en-us", "-s", "150", "-w", "src.wav", "Hello, this is a short test of voice conversion on a phone."], check=True)
subprocess.run(["espeak-ng", "-v", "en-us+f3", "-s", "140", "-p", "70", "-w", "ref.wav", "The quick brown fox jumps over the lazy dog near the river bank."], check=True)
src, ref = load("src.wav"), load("ref.wav")
se_src, se_ref = embed(src), embed(ref)
print("embed", se_src.shape, float(np.abs(se_src).mean()), float(np.abs(se_ref).mean()), "diff", float(np.abs(se_src - se_ref).mean()))
sp = spec(src)[None]
out = cvt.run(None, {
    "audio": sp, "audio_length": np.array([sp.shape[2]], dtype=np.int64),
    "src_tone": se_src, "dest_tone": se_ref, "tau": np.array([0.3], dtype=np.float32),
})[0][0, 0]
print("out", out.shape, "src samples", len(src), "range", float(out.min()), float(out.max()), "finite", bool(np.isfinite(out).all()))
wavfile.write("out.wav", SR, (np.clip(out, -1, 1) * 32767).astype(np.int16))
assert np.isfinite(out).all() and abs(len(out) - len(src)) < 2048 and np.abs(out).max() > 0.01
print("PIPELINE OK")
