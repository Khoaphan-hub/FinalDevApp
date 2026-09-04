# Nhập lịch trình từ QR trong PDF

## Cách dùng

1. Cập nhật app và backend cùng phiên bản này. Không cần migration mới.
2. Mở **Chuyến đi → Nhập lịch trình**.
3. Chọn **Quét mã QR** để đọc mã trên một màn hình khác/bản in, hoặc **Chọn ảnh QR** để đọc ảnh trong điện thoại. Nếu đang có PDF trên điện thoại, chụp màn hình trang cuối chứa QR rồi chọn ảnh đó; chức năng này không đọc trực tiếp file PDF.
4. App tải lịch trình và mở màn hình **XEM TRƯỚC • CHƯA LƯU**. Có thể xem các ngày, địa điểm, bản đồ hoặc thay địa điểm trước khi lưu.
5. Bấm **Lưu vào chuyến đi**. Chỉ lúc này Room mới được ghi; app quay về tab Chuyến đi. Quay lại mà chưa bấm lưu không tạo lịch trình đã lưu.

Giao diện có tiếng Việt/Anh, nút camera/ảnh riêng, hướng dẫn cho PDF nằm cùng điện thoại, thông báo lỗi và thử lại khi mất kết nối. Không cần cấp quyền toàn bộ thư viện; chỉ ảnh được chọn được đọc. Camera chỉ xin quyền khi bấm quét.

## Backend và QR cũ

- Endpoint mới: `GET /api/mobile/itineraries/import/<token>/`.
- Nhập bằng token trong liên kết `/resume/<token>/`, không tải URL bất kỳ được mã hóa trong QR. App chỉ gọi backend đã cấu hình trong `RemotePlannerRepository.DEFAULT_BASE_URL`.
- Vì thế QR từng xuất từ emulator với host `10.0.2.2` có thể được đọc trong app trên điện thoại nếu app điện thoại trỏ tới cùng backend/cùng database bằng địa chỉ truy cập được. Không tự khám phá server khác; không thể lấy lịch trình ở database khác dù hình QR đúng.
- Dữ liệu QR cũ vẫn được đọc khi token còn trong database và chưa hết hạn. QR có thời hạn 30 ngày. Endpoint trả 410 khi hết hạn, 404 khi không tồn tại, 400/422 khi mã hoặc snapshot không hợp lệ.
- Token là quyền đọc lịch trình, tương tự link resume trên web: người có mã có thể xem lịch trình. Không đưa QR của chuyến đi riêng tư lên nơi công khai nếu không muốn chia sẻ.
- Không thêm địa điểm, không sửa ma trận khoảng cách, không đổi session của website, không ghi lịch trình cộng đồng.
- Tên, thứ tự ngày/địa điểm, tọa độ, chi phí tổng lấy từ snapshot. PDF tạo mới lưu thêm giá từng điểm và một số thông tin chi tiết; QR cũ thiếu các trường này dùng dữ liệu catalog hiện tại. Ảnh và link review được lấy từ catalog trên backend, không lấy URL ảnh tùy ý từ snapshot.
- Backend đang chạy với `--noreload` cần khởi động lại. Với `runserver` mặc định, theo dõi terminal để bảo đảm phiên bản mới đã được nạp. Không cần migrate cho riêng chức năng này.

## Các file chính

- `presentation/importtrip/`: màn hình nhập, camera và ViewModel giữ tác vụ khi xoay màn hình.
- `domain/model/ItineraryQrLink.java`: xác thực cấu trúc QR và trích token.
- `infrastructure/device/QrImageReader.java`: giảm kích thước và giải mã ảnh trên thiết bị; không upload.
- `backend/home/mobile_import.py`: đọc snapshot còn hạn và trả dữ liệu native.
- `backend/home/mobile_share.py`, `RemoteItineraryShareRepository.java`: giữ thêm thông tin khi xuất PDF mới.

Quét và giải mã dùng [ZXing Android Embedded 4.3.0](https://github.com/journeyapps/zxing-android-embedded), tương thích cấu hình minSdk 24 của dự án. API import dùng bản snapshot có sẵn; không cần tạo lại thuật toán.

## Checklist tự kiểm tra sau build

Ngày 04/09/2026: build APK `:app:assembleDebug` và kiểm tra cú pháp Python thành công. Sau đó người dùng yêu cầu kiểm thử trên máy ảo; đã cài cập nhật giữ nguyên dữ liệu và chạy các ca thực tế. Chi tiết xem `QR_IMPORT_TEST_REPORT.md`.

Đã kiểm tra thành công trên emulator:

- PNG trang QR của PDF cũ → xem trước đúng lịch trình 3 ngày và chi phí; đổi ngày.
- Thoát khi chưa lưu không tạo bản lưu mới; lưu tự trở về Chuyến đi và mở lại được.
- Từ chối/cấp quyền camera, mở/đóng màn hình camera.
- Ảnh không QR, nhiều QR, QR không hợp lệ và token không tồn tại đều hiện thông báo phù hợp.
- Mất mạng → lỗi có thử lại; giữ trạng thái qua xoay ngang/dọc; nối mạng và thử lại không cần chọn lại ảnh.
- Nhãn và hướng dẫn tiếng Việt/Anh.

Chưa xác nhận: camera nhận QR trực tiếp (cảnh camera ảo chưa đưa mã vào khung), mã hết hạn, snapshot hỏng, vòng xuất PDF mới rồi nhập lại, ảnh JPEG/rất lớn, nhiều cỡ màn hình/chữ lớn. Chưa chạy lại unit test/lint trong lượt test QR.

Quét QR bên ngoài app vẫn mở web như trước. Chưa thêm Android App Links/deep links, chưa thêm đọc trực tiếp PDF, chưa thay đổi luồng đăng nhập/offline hoặc chức năng cộng đồng.
