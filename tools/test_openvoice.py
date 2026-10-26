"""Desktop check of the OpenVoice V2 ONNX tone-color pipeline that the Android app re-implements in Kotlin."""
import subprocess
import numpy as np
from scipy.io import wavfile
from scipy.signal import resample_poly

SR, NFFT, HOP = 22050, 1024, 256
win = np.hanning(NFFT + 1)[:-1].astype(np.float32)  # periodic hann like torch.hann_window


def spec(y):
    """Magnitude STFT [513, T]; mirrors Spectrogram.compute in OpenVoice.kt."""
    pad = (NFFT - HOP) // 2
    y = np.pad(y, (pad, pad), mode="reflect")
    n = 1 + (len(y) - NFFT) // HOP
    frames = np.stack([y[i * HOP:i * HOP + NFFT] * win for i in range(n)])
    s = np.fft.rfft(frames, axis=1)
    return np.sqrt(s.real ** 2 + s.imag ** 2 + 1e-6).T.astype(np.float32)


def load(path):
    sr, y = wavfile.read(path)
    y = y.astype(np.float32) / 32768.0
    if sr != SR:
        g = np.gcd(sr, SR)
        y = resample_poly(y, SR // g, sr // g).astype(np.float32)
    return y


if __name__ == "__main__":
    from huggingface_hub import hf_hub_download
    import onnxruntime as ort

    ext = ort.InferenceSession(hf_hub_download("eugenehp/openvoice", "tone_extract.onnx"))
    cvt = ort.InferenceSession(hf_hub_download("eugenehp/openvoice", "tone_color.onnx"))

    def embed(y):
        return ext.run(None, {"input": np.ascontiguousarray(spec(y)[None].transpose(0, 2, 1))})[0].reshape(1, 256, 1)

    subprocess.run(["espeak-ng", "-v", "en-us", "-w", "src.wav", "Hello, this is a short test of voice conversion on a phone."], check=True)
    subprocess.run(["espeak-ng", "-v", "en-us+f3", "-p", "70", "-w", "ref.wav", "The quick brown fox jumps over the lazy dog."], check=True)
    src, ref = load("src.wav"), load("ref.wav")
    sp = spec(src)[None]
    out = cvt.run(None, {"audio": sp, "audio_length": np.array([sp.shape[2]], dtype=np.int64),
                         "src_tone": embed(src), "dest_tone": embed(ref), "tau": np.array([0.3], dtype=np.float32)})[0][0, 0]
    print("out", out.shape, "src samples", len(src), "finite", bool(np.isfinite(out).all()))
    assert np.isfinite(out).all() and abs(len(out) - len(src)) < 2048 and np.abs(out).max() > 0.01
    print("PIPELINE OK")
