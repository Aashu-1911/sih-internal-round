#!/bin/bash
# Download IndicConformer INT8 ONNX models for STT
# Models from: https://huggingface.co/parismitaglobalsolutions/indicconformer-sherpa-onnx
# Total: ~1.7GB for 9 languages
#
# Usage: ./scripts/download-stt-models.sh
# Run from project root.

set -e

ASSETS_DIR="app/src/main/assets"
REPO="https://huggingface.co/parismitaglobalsolutions/indicconformer-sherpa-onnx/resolve/main"

# Languages to download (code:model_size_mb)
declare -A MODELS=(
    ["hi"]=189
    ["gu"]=189
    ["te"]=189
    ["en"]=167
    ["bn"]=189
    ["mr"]=189
    ["ta"]=189
    ["kn"]=189
    ["ml"]=189
)

# Shared tokens file (Indic languages)
TOKENS_URL="${REPO}/tokens.txt"

echo "=== Downloading STT models ==="
echo "Languages: ${!MODELS[*]}"
echo ""

# Download shared tokens
echo "Downloading shared tokens.txt..."
mkdir -p /tmp/stt-tokens
curl -sL -o /tmp/stt-tokens/tokens.txt "${TOKENS_URL}"
echo "  tokens.txt: $(wc -l < /tmp/stt-tokens/tokens.txt) tokens"

# Download each language model
for lang in "${!MODELS[@]}"; do
    echo ""
    echo "--- ${lang} ---"
    
    # Create asset directory
    TARGET_DIR="${ASSETS_DIR}/stt-${lang}"
    mkdir -p "${TARGET_DIR}"
    
    # Download model
    MODEL_URL="${REPO}/${lang}/model.int8.onnx"
    echo "  Downloading model.int8.onnx..."
    curl -L --progress-bar -o "${TARGET_DIR}/model.int8.onnx" "${MODEL_URL}"
    
    # Copy tokens
    if [ "${lang}" = "en" ]; then
        # English has its own tokens
        EN_TOKENS_URL="${REPO}/${lang}/tokens.txt"
        echo "  Downloading English tokens.txt..."
        curl -sL -o "${TARGET_DIR}/tokens.txt" "${EN_TOKENS_URL}"
    else
        # Indic languages share tokens
        cp /tmp/stt-tokens/tokens.txt "${TARGET_DIR}/tokens.txt"
    fi
    
    SIZE=$(du -sh "${TARGET_DIR}" | cut -f1)
    echo "  Done: ${SIZE}"
done

# Cleanup
rm -rf /tmp/stt-tokens

echo ""
echo "=== All models downloaded ==="
echo "Total:"
du -sh ${ASSETS_DIR}/stt-*/
echo ""
echo "Grand total:"
du -sh ${ASSETS_DIR}/stt-*/ | tail -1
