# YouTube Lite for Android 5.0+ (API 21)
Dự án Android WebView tối ưu cho thiết bị chạy Android 5.0 (Lollipop) trở lên.

## Đặc điểm nổi bật:
- minSdk: 21 (Hỗ trợ 100% Android 5.0 Lollipop)
- targetSdk: 33 (Tương thích Android 13/14)
- Dung lượng APK sau khi build: ~1.8 MB (giảm 98% so với app YouTube gốc)
- Tiêu thụ RAM trung bình: ~80-110 MB (phù hợp máy ram 512MB - 1GB)
- Tích hợp chặn quảng cáo tầng mạng (shouldInterceptRequest) và tiêm CSS/JS
- Hỗ trợ xem video toàn màn hình (WebChromeClient)
- Xử lý nút Back cứng chống vô tình thoát app

## Cách mở trong Android Studio:
1. Giải nén thư mục này.
2. Mở Android Studio -> Chọn "Open" -> Trỏ vào thư mục này.
3. Chờ Gradle đồng bộ (Sync) hoàn tất.
4. Bấm "Run" (phím tắt Shift + F10) hoặc Build -> Build Bundle(s) / APK(s) -> Build APK(s).

## Lưu ý chữ ký khi cài lên Android 5.0:
Khi tạo Signed APK, bạn BẮT BUỘC phải tích chọn "V1 (JAR Signature)"!
Android 5.0 không hỗ trợ cài đặt file APK chỉ có chữ ký V2/V3.
