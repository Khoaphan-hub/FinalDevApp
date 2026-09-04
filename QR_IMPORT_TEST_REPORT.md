# Kiểm thử nhập QR trên emulator — 04/09/2026

## Xác nhận bổ sung từ người dùng — S23, backend local

Sau khi ghép các cập nhật của nhóm tới `1eacf55` và cài bản local lên S23, người dùng xác nhận đã tự test và tính năng QR hoạt động đúng, đồng thời cho phép commit/push nhánh hiện tại. Đây là xác nhận của người dùng; không coi là đã kiểm tra mọi trường hợp biên hoặc backend online. Các mục bên dưới ghi lại phạm vi kiểm thử emulator trước đó.

## Mốc 1: luồng chính đã kiểm tra

- Máy ảo `emulator-5554`; cài cập nhật APK bằng `install -r`, không xóa dữ liệu app.
- Backend có sẵn tại cổng 8000 trả endpoint import HTTP 200; không restart hoặc migrate.
- Đăng nhập bằng tài khoản QA cục bộ. Chọn đúng ảnh trang cuối của PDF `QA0902-Integration` đã xuất ngày 02/09.
- Mở được bản xem trước 3 ngày, chi phí 1.377.000 ₫, còn lại 1.623.000 ₫; ngày 1 có Hoi An Quang Noodles, chuyển ngày 2 có Pho Vy.
- Thoát chưa lưu: danh sách vẫn đúng hai chuyến đi ban đầu.
- Nhập lại rồi bấm Lưu vào chuyến đi: tự trở về tab Chuyến đi; xuất hiện bản mới `QA0902-Integration • Hoi An Quang Noodles` lúc 11:40. Mở lại được đúng dữ liệu và không còn nhãn chưa lưu.
- Hai chuyến đi ban đầu được giữ nguyên. Bản QA nhập mới được để lại để người dùng tự kiểm tra.
- Từ chối quyền camera: hiện hướng dẫn cấp quyền hoặc chọn ảnh, không crash.

## Mốc 2: trạng thái lỗi, quyền và phục hồi

| Ca kiểm tra | Kết quả thực tế |
| --- | --- |
| Cho phép camera sau lần từ chối | Camera ảo mở, có khung quét và hướng dẫn |
| Đóng camera | Trở về màn hình nhập, hiện thông báo chưa chọn mã |
| QR chứa URL không phải lịch trình | Báo mã Journify không hợp lệ, không mở URL bên ngoài |
| Ảnh không chứa QR | Báo chưa tìm thấy mã và hướng dẫn chọn ảnh rõ nét |
| Ảnh chứa hai QR | Báo cần cắt ảnh để chỉ còn mã muốn nhập (đã thử tiếng Anh) |
| QR đúng định dạng nhưng token không tồn tại | Hiện Trip not found on this backend và nút thử lại; không mở host example.invalid trong QR |
| Tắt cả Wi-Fi và dữ liệu di động của emulator, nhập QR hợp lệ | Báo không kết nối backend, có nút thử lại |
| Xoay ngang rồi dọc ở trạng thái lỗi mạng | Thông báo và nút thử lại còn nguyên, cuộn tới trạng thái lỗi |
| Bật mạng lại và thử lại | Mở đúng lịch trình mà không chọn lại ảnh |
| Đổi ngôn ngữ sang tiếng Anh | Thẻ ở Trips, màn hình nhập và lỗi nhiều QR dùng tiếng Anh |
| Nhật ký crash của lần kiểm tra | Không có bản ghi crash trong khoảng 11:xx ngày 04/09; log cũ trước lần thử không được coi là kết quả lần này |

Wi-Fi, dữ liệu di động, tự xoay và hướng xoay đã khôi phục về lần lượt `1`, `1`, `1`, `0`. Ảnh gắn thử vào cảnh camera ảo đã trả về mặc định. Không restart backend, migrate, xóa dữ liệu người dùng, chỉnh ma trận, commit hoặc push.

## Chưa xác nhận / ngoài phạm vi lần này

- Quét quang học QR bằng camera: đã mở/đóng camera và kiểm tra quyền, nhưng cảnh camera ảo hiện tại không đưa QR vào khung nhìn. Chưa đánh dấu camera decode end-to-end pass; cần thử mã trên màn hình khác bằng điện thoại thật.
- Mã hết hạn 30 ngày, snapshot hỏng, PDF xuất mới trong cùng vòng kiểm thử này, ảnh JPEG/ảnh dung lượng rất lớn, nhiều cỡ thiết bị/chữ lớn chưa thử.
- Không kiểm thử lại toàn bộ app hoặc luồng cộng đồng đang được thành viên khác chỉnh sửa.
- Không có thay đổi mã ứng dụng phát sinh trong lượt test này. Dùng APK đã build thành công ở mốc tính năng trước; không chạy lại unit test/lint.

Ảnh minh chứng cục bộ (thư mục `tmp/` bị Git bỏ qua): `qr-import-screen.png`, `qr-camera.png`. Các ảnh QA được thêm vào Downloads của emulator để có thể thử lại.
