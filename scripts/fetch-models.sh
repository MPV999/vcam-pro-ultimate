#!/usr/bin/env bash
# Tải model MediaPipe Face Landmarker (tuỳ chọn) và kiểm tra checksum.
# Không bắt buộc: thiếu model thì AI Vision dùng ML Kit bundled.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$ROOT/android/app/src/main/assets/models"
MODEL_URL="https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task"
MODEL_FILE="face_landmarker.task"

# SHA-256 của file model chính thức. Nếu Google phát hành bản mới, cập nhật giá trị này
# bằng cách chạy: sha256sum face_landmarker.task
EXPECTED_SHA256="${VCAM_MODEL_SHA256:-}"

mkdir -p "$DEST"

if [ -f "$DEST/$MODEL_FILE" ]; then
  echo "Model đã tồn tại: $DEST/$MODEL_FILE"
  exit 0
fi

echo "Đang tải model MediaPipe Face Landmarker…"
curl -fL --retry 3 -o "$DEST/$MODEL_FILE" "$MODEL_URL"

ACTUAL_SHA256="$(sha256sum "$DEST/$MODEL_FILE" | awk '{print $1}')"
echo "SHA-256: $ACTUAL_SHA256"

if [ -n "$EXPECTED_SHA256" ]; then
  if [ "$ACTUAL_SHA256" != "$EXPECTED_SHA256" ]; then
    echo "Checksum KHÔNG khớp. Xoá file và dừng lại." >&2
    rm -f "$DEST/$MODEL_FILE"
    exit 1
  fi
  echo "Checksum khớp."
else
  echo "Chưa đặt VCAM_MODEL_SHA256 — hãy tự đối chiếu checksum trước khi dùng cho bản phát hành."
fi

echo "Xong. Model nằm tại: $DEST/$MODEL_FILE"
echo "Lưu ý: file này KHÔNG được commit (đã nằm trong .gitignore)."
