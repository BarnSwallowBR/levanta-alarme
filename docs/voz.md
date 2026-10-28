# Voz das frases — pesquisa e decisão

Objetivo: voz bem melhor que o TTS robótico, **custo zero**, funcionando **offline às 6h**.

| Opção | Qualidade | Offline | APK | Risco | Veredito |
|---|---|---|---|---|---|
| a) Gravação da própria voz (MediaRecorder) | Real, a mais pessoal | Sim | ~0 (fica no celular) | Baixo | **Usar** |
| b) Áudio neural pré-gerado (edge-tts, vozes Antônio/Francisca/Thalita) | Quase humana | Sim (vai no APK) | +1,7 MB (20 frases × 3 vozes, MP3 64 kbps mono) | Baixo no app; o serviço gratuito do Edge pode mudar, mas só afeta regerar | **Usar** |
| b') Piper TTS pt_BR pré-gerado | Média (voz "faber" soa sintética) | Sim | Similar | Baixo | Reserva se o edge-tts parar |
| c) TTS neural no celular (sherpa-onnx + Piper) | Média | Sim | +60–80 MB (modelo + libs nativas) | Alto sem teste em aparelho real | Descartado por ora |
| d) Melhor voz do sistema (TextToSpeech.getVoices) | Boa com "Serviços de voz do Google" | Se a voz for local | 0 | Baixo | **Usar como último recurso** |

## Decisão
Cada frase toca o melhor áudio disponível, nesta ordem:

1. **Gravação do usuário** daquela frase (Ajustes → frase → ● Gravar).
2. **Voz neural empacotada** escolhida (padrão: Antônio), para as 20 frases padrão com os nomes padrão.
3. **Voz do celular** escolhida em Ajustes, com velocidade e tom.

O áudio é ligado à frase pelo hash do texto já com `{nome}`/`{pessoa}` trocados
(`VoiceClips.key`, igual ao `key()` do script). Editar a frase ou trocar o nome faz ela cair
para o próximo nível automaticamente.

## Regerar as vozes neurais
```
python3 -m venv /tmp/venv-voz && /tmp/venv-voz/bin/pip install edge-tts
/tmp/venv-voz/bin/python scripts/gerar-vozes.py          # gera o que falta
/tmp/venv-voz/bin/python scripts/gerar-vozes.py --force  # regera tudo
```
Precisa de internet e `ffmpeg`. O script lê as frases de `SettingsRepository.kt`.
