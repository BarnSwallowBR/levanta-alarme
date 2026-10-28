#!/usr/bin/env python3
"""Gera os áudios neurais das frases padrão e grava em app/src/main/assets/voice/.

As frases e os nomes padrão são lidos direto de SettingsRepository.kt, então basta rodar de
novo depois de mudar a lista. Cada áudio é nomeado pelo hash do texto já com os nomes
trocados (mesma regra de VoiceClips.key no app), então frase editada no celular perde o
áudio automaticamente e cai na voz do sistema.

Uso:
    python3 -m venv /tmp/venv-voz && /tmp/venv-voz/bin/pip install edge-tts
    /tmp/venv-voz/bin/python scripts/gerar-vozes.py            # gera o que falta
    /tmp/venv-voz/bin/python scripts/gerar-vozes.py --force    # regera tudo

Precisa de internet (edge-tts usa o serviço de leitura em voz alta do Microsoft Edge, grátis
e sem chave) e de ffmpeg com libmp3lame.
"""
import argparse
import asyncio
import hashlib
import json
import re
import shutil
import subprocess
import sys
import tempfile
import unicodedata
from pathlib import Path

import edge_tts

ROOT = Path(__file__).resolve().parent.parent
SETTINGS_KT = ROOT / "app/src/main/java/br/com/barnswallow/levanta/data/SettingsRepository.kt"
OUT = ROOT / "app/src/main/assets/voice"

# id usado no app (VoiceRepository) -> voz do edge-tts
VOICES = {
    "antonio": "pt-BR-AntonioNeural",
    "francisca": "pt-BR-FranciscaNeural",
    "thalita": "pt-BR-ThalitaMultilingualNeural",
}

# Remove silêncio do começo e do fim, nivela o volume alto (é um alarme) e comprime.
FFMPEG_FILTER = (
    "silenceremove=start_periods=1:start_threshold=-50dB,"
    "areverse,silenceremove=start_periods=1:start_threshold=-50dB,areverse,"
    "loudnorm=I=-14:TP=-1:LRA=11"
)


def key(text: str) -> str:
    """Igual a VoiceClips.key: NFC, espaços colapsados, SHA-256, 16 primeiros hex."""
    norm = " ".join(unicodedata.normalize("NFC", text).split())
    return hashlib.sha256(norm.encode("utf-8")).hexdigest()[:16]


def read_defaults() -> tuple[list[str], str, str]:
    src = SETTINGS_KT.read_text(encoding="utf-8")
    user = re.search(r'DEFAULT_USER\s*=\s*"([^"]*)"', src).group(1)
    person = re.search(r'DEFAULT_PERSON\s*=\s*"([^"]*)"', src).group(1)
    block = re.search(r"DEFAULT_PHRASES\s*=\s*listOf\((.*?)\n\s*\)", src, re.S).group(1)
    phrases = re.findall(r'"((?:[^"\\]|\\.)*)"', block)
    if not phrases:
        sys.exit("Não achei DEFAULT_PHRASES em SettingsRepository.kt")
    return phrases, user, person


async def synth(text: str, voice: str, dest: Path) -> None:
    with tempfile.TemporaryDirectory() as tmp:
        raw = Path(tmp) / "raw.mp3"
        await edge_tts.Communicate(text, voice).save(str(raw))
        subprocess.run(
            ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(raw),
             "-af", FFMPEG_FILTER, "-ar", "24000", "-ac", "1",
             "-c:a", "libmp3lame", "-b:a", "64k", str(dest)],
            check=True,
        )


async def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--force", action="store_true", help="regera mesmo se o arquivo existir")
    args = parser.parse_args()
    if not shutil.which("ffmpeg"):
        sys.exit("Instale o ffmpeg")

    phrases, user, person = read_defaults()
    rendered = [p.replace("{nome}", user).replace("{pessoa}", person) for p in phrases]
    manifest = {"voices": {}, "phrases": {key(t): t for t in rendered}}

    for vid, voice in VOICES.items():
        folder = OUT / vid
        folder.mkdir(parents=True, exist_ok=True)
        wanted = set()
        for text in rendered:
            dest = folder / f"{key(text)}.mp3"
            wanted.add(dest.name)
            if dest.exists() and not args.force:
                continue
            print(f"[{vid}] {text}")
            await synth(text, voice, dest)
        for stale in folder.glob("*.mp3"):
            if stale.name not in wanted:
                stale.unlink()
        manifest["voices"][vid] = voice

    (OUT / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    total = sum(f.stat().st_size for f in OUT.rglob("*.mp3"))
    print(f"Pronto: {len(rendered)} frases x {len(VOICES)} vozes, {total / 1024:.0f} KB em {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    asyncio.run(main())
