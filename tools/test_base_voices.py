"""Which Piper archive variants actually produce audio in sherpa-onnx? Mirrors VoxCore.baseEngine()."""
import tarfile, urllib.request, os, sys
import numpy as np
import sherpa_onnx

REL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"
TEXT = {"en": "Hey Daniel, how are you doing?", "ar": "مرحبا كيف حالك اليوم", "zh": "你好，今天怎么样？"}
CASES = [
    ("en", "vits-piper-en_US-lessac-medium-int8"),
    ("ar", "vits-piper-ar_JO-kareem-medium-int8"),
    ("ar", "vits-piper-ar_JO-kareem-medium"),
    ("zh", "vits-piper-zh_CN-chaowen-medium-int8"),
]
print("sherpa-onnx", sherpa_onnx.__version__ if hasattr(sherpa_onnx, "__version__") else "?", flush=True)
for lang, name in CASES:
    arc = f"{name}.tar.bz2"
    try:
        urllib.request.urlretrieve(f"{REL}/{arc}", arc)
    except Exception as e:
        print(f"RESULT {name}: DOWNLOAD FAILED {e}", flush=True); continue
    tarfile.open(arc).extractall(".")
    files = sorted(os.listdir(name))
    onnx = [f for f in files if f.endswith(".onnx")][0]
    size = os.path.getsize(f"{name}/{onnx}") // 1_000_000
    lex = f"{name}/lexicon.txt" if os.path.exists(f"{name}/lexicon.txt") else ""
    try:
        cfg = sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(
            vits=sherpa_onnx.OfflineTtsVitsModelConfig(model=f"{name}/{onnx}", tokens=f"{name}/tokens.txt",
                                                      lexicon=lex, data_dir=os.path.abspath(f"{name}/espeak-ng-data") if "espeak-ng-data" in files else ""),
            num_threads=2))
        a = sherpa_onnx.OfflineTts(cfg).generate(TEXT[lang], sid=0, speed=1.0)
        n = len(a.samples)
        print(f"RESULT {name}: {'OK' if n > 1000 else 'EMPTY'} {n} samples @ {a.sample_rate} Hz, model {size} MB, files {files}", flush=True)
    except Exception as e:
        print(f"RESULT {name}: ERROR {e}; files {files}", flush=True)
