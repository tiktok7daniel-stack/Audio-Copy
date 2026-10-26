import sys, os
from huggingface_hub import HfApi, hf_hub_download
import onnx

api = HfApi()
for repo in ["aoiandroid/OpenVoiceV2-ONNX-mirror", "eugenehp/openvoice", "NowLoadY/XRTranslate-OpenVoice-ONNX"]:
    print("=" * 80, "\nREPO", repo, flush=True)
    try:
        info = api.model_info(repo, files_metadata=True)
        for s in info.siblings:
            print(f"  {s.rfilename}  {s.size}")
        print("  license:", getattr(info, "card_data", None) and info.card_data.get("license"))
        for s in info.siblings:
            if s.rfilename.lower() in ("readme.md",):
                p = hf_hub_download(repo, s.rfilename)
                print(open(p, encoding="utf-8", errors="replace").read()[:4000])
            if s.rfilename.endswith(".onnx") and (s.size or 0) < 200_000_000:
                p = hf_hub_download(repo, s.rfilename)
                m = onnx.load(p, load_external_data=False)
                def d(v):
                    t = v.type.tensor_type
                    return v.name, [x.dim_param or x.dim_value for x in t.shape.dim], t.elem_type
                print("  MODEL", s.rfilename)
                print("    in :", [d(v) for v in m.graph.input if v.name not in {i.name for i in m.graph.initializer}])
                print("    out:", [d(v) for v in m.graph.output])
    except Exception as e:
        print("  ERR", e)
