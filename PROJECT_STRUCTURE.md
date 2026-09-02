# Project Structure: JournifyAndroid

Dưới đây là cấu trúc cây thư mục của dự án **JournifyAndroid**, mô tả vai trò của từng thành phần chính.

## 📂 Root Directory
- **app/**: Module chính của ứng dụng Android.
- **backend/**: Thư mục chứa mã nguồn backend (Django), script xử lý dữ liệu và các file CSV/Database cho AI/Chatbot.
- **gradle/**: Chứa Gradle Wrapper để build dự án.
- **build.gradle.kts**: Cấu hình build cấp dự án (Project level).
- **settings.gradle.kts**: Khai báo các module trong dự án.
- **gradle.properties**: Các cấu hình thuộc tính của Gradle.
- **local.properties**: Chứa đường dẫn SDK Android (không nên commit).
- **README.md**: Hướng dẫn chung của dự án.
- **JOURNIFY_MIGRATION.md**: Tài liệu hướng dẫn migration/cập nhật dự án.

---

## 📂 app/ (Android App Module)
Nơi chứa toàn bộ mã nguồn Java/Kotlin và tài nguyên giao diện của ứng dụng.

### 📦 src/main/java/com/example/finalproject/
Dự án được tổ chức theo kiến trúc phân lớp (Layered Architecture):

- **domain/**: Chứa các thành phần cốt lõi, không phụ thuộc vào framework.
    - **model/**: Các đối tượng dữ liệu chính (Itinerary, TripRequest, Place, Mood...).
    - **repository/**: Các Interface định nghĩa cách truy xuất dữ liệu.
    - **callback/**: Các functional interface cho xử lý bất đồng bộ.

- **application/**: Chứa logic nghiệp vụ ứng dụng (Use Cases).
    - **usecase/**: Các class thực hiện một chức năng nghiệp vụ cụ thể.

- **infrastructure/**: Triển khai chi tiết các repository (Data Layer).
    - **local/**: Sử dụng Room Database để lưu trữ dữ liệu offline (DAO, Entities, DB config).
    - **remote/**: Kết nối API, lấy dữ liệu từ backend/internet (Retrofit/Network logic).
    - **demo/**: Chứa các implementation giả lập (Fake data) để test hoặc demo nhanh.

- **presentation/**: Lớp giao diện người dùng (UI Layer).
    - **MainActivity.java**: Activity chính quản lý điều hướng.
    - **home/**, **planner/**, **itinerary/**, **map/**, **saved/**, **catalog/**, **selection/**: Mỗi folder đại diện cho một tính năng lớn, chứa Fragment/Activity và ViewModel tương ứng.
    - **ComingSoonFragment.java**: Màn hình chờ cho các tính năng chưa hoàn thiện.

### 📦 src/main/res/ (Resources)
- **layout/**: Các file XML định nghĩa giao diện (Activities, Fragments, Item lists).
- **drawable/**: Hình ảnh, icon vector, và các định dạng đồ họa khác.
- **values/**: Chứa strings.xml (đa ngôn ngữ), colors.xml (bảng màu), themes.xml (style ứng dụng).
- **menu/**: Định nghĩa menu (như bottom navigation).
- **xml/**: Các cấu hình bổ sung (như network security, file paths).

---

## 📂 backend/ (Python/Django Backend)
Chứa hệ thống máy chủ và các công cụ hỗ trợ dữ liệu.

- **firstsite/**: Thư mục cấu hình chính của Django project.
- **home/**: Ứng dụng chính xử lý các API cho mobile.
- **manage.py**: Script quản lý Django.
- **db.sqlite3**: Database cục bộ của backend.
- **dalat_pois.csv**, **dalat_eateries.csv**...: Dữ liệu về địa điểm du lịch tại Đà Lạt phục vụ cho AI/Chatbot.
- **chatbot_vectors.pkl**: File vector đã được train cho chatbot.
- **scripts (.py)**: Các script Python hỗ trợ dịch thuật, sửa lỗi địa chỉ, cập nhật template dữ liệu.
- **Mobile_Installation_README.md**: Hướng dẫn cài đặt môi trường cho mobile.
