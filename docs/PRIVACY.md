# Chính sách riêng tư — VCAM PRO ULTIMATE VIP 2026

Cập nhật: 01/01/2026

## 1. Cam kết cốt lõi

Ứng dụng **không** khai báo quyền `android.permission.INTERNET` trong `AndroidManifest.xml`.
Ở mức hệ điều hành Android, ứng dụng không thể mở kết nối mạng. Đây là ràng buộc kỹ thuật,
không phải tuyên bố marketing.

## 2. Dữ liệu lưu trên thiết bị

- Ảnh/video do bạn chụp, quay hoặc import.
- Hồ sơ khuôn mặt: UID, tên, nhóm, mô tả, thumbnail, metadata, ghi chú đồng ý.
- Kết quả phân tích AI: số khuôn mặt, landmark, điểm chất lượng, thời gian xử lý.
- Hàng đợi tác vụ, lịch sử xuất file (kèm SHA-256).
- Cấu hình ứng dụng (DataStore) và nhật ký hoạt động local.

Tất cả nằm trong thư mục dữ liệu riêng của ứng dụng (`/data/data/com.vcampro.ultimate/`),
trừ khi bạn chủ động export/chia sẻ.

## 3. Dữ liệu không bao giờ thu thập

- Không analytics, không crash reporting từ xa, không quảng cáo, không tracking.
- Không tài khoản, không đăng nhập, không định danh thiết bị.
- Không vị trí, danh bạ, tin nhắn, lịch sử cuộc gọi.
- Không OTP, PIN ngân hàng hay mật khẩu dịch vụ khác.
- Không dữ liệu sinh trắc học thô (vân tay/khuôn mặt do OS quản lý, ứng dụng chỉ nhận kết quả đúng/sai).

## 4. Quyền ứng dụng

| Quyền | Mục đích |
|---|---|
| `CAMERA` | Chụp ảnh, quay video trong Camera Studio |
| `RECORD_AUDIO` | Ghi âm khi quay video (tuỳ chọn) |
| `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` | Đọc file bạn chọn để import |
| `READ_EXTERNAL_STORAGE` (API ≤ 32) | Tương thích Android cũ |
| `USE_BIOMETRIC` | Mở khoá ứng dụng |
| `POST_NOTIFICATIONS` | Thông báo khi tác vụ nền xong |
| `WAKE_LOCK` | Giữ CPU khi export video dài |
| `INTERNET` | **Không khai báo** |

## 5. Sao lưu

`android:allowBackup="false"` và `dataExtractionRules` loại trừ database, shared preferences
và file khỏi cloud backup của hệ điều hành. Dữ liệu không được đưa lên Google Drive.

Sao lưu trong ứng dụng (Settings → Sao lưu) tạo file JSON **trong bộ nhớ riêng của ứng dụng**.

## 6. Mã hoá

- Khoá AES-256-GCM trong Android Keystore, không thể export khỏi thiết bị.
- PIN băm bằng PBKDF2-HMAC-SHA256, 120.000 vòng, salt 16 byte ngẫu nhiên.
- Khi bạn chọn "Xoá toàn bộ dữ liệu", khoá Keystore cũng bị huỷ.

## 7. Chia sẻ

Dữ liệu chỉ rời thiết bị khi bạn chủ động export hoặc dùng hộp thoại chia sẻ của Android.
Không có cơ chế upload tự động.

## 8. Xoá dữ liệu

- Xoá từng file trong Thư viện (ghi đè best-effort rồi xoá).
- Xoá toàn bộ trong Security Center.
- Gỡ ứng dụng: Android xoá toàn bộ thư mục dữ liệu riêng.

## 9. Trẻ em

Ứng dụng không hướng tới trẻ em dưới 13 tuổi, không có nội dung tải về, không có mạng xã hội.

## 10. Sử dụng có trách nhiệm

Ứng dụng không cung cấp camera ảo, không giả mạo danh tính, không vượt qua xác thực
sinh trắc học/liveness và không can thiệp vào ứng dụng của bên thứ ba.
Khi ghi hình người khác, bạn cần có sự đồng ý của họ — ứng dụng có trường
"Consent Record" trong hồ sơ để lưu xác nhận này.

## 11. Thay đổi

Mọi thay đổi được công bố tại trang này kèm ngày cập nhật.
