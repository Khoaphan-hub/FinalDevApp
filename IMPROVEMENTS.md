# Journify — Rà soát chất lượng & danh sách cải thiện

**Ngày rà soát:** 01/09/2026 · **Deadline theo `JOURNIFY_MIGRATION.md`:** 23:59 ngày 05/09/2026
**Phạm vi:** toàn bộ `app/src` (33 file Java, ~2.700 dòng), `AndroidManifest.xml`, `res/`, `gradle/`, `backend/`.

Mỗi mục có nhãn mức độ:
- 🔴 **P0** — lỗi thật / mất điểm rubric, nên sửa trước khi nộp
- 🟡 **P1** — ảnh hưởng "Technical quality (25%)" và "UI/UX (20%)", nên làm nếu còn thời gian
- 🟢 **P2** — chỉ làm nếu dư thời gian, hoặc ghi vào phần "hướng phát triển" của báo cáo

---

## A. LỖI THẬT — cần sửa (🔴 P0)

### A1. Manifest khai báo 2 Activity không tồn tại
`app/src/main/AndroidManifest.xml:40` và `:44` khai báo `.presentation.auth.LoginActivity` và
`.presentation.auth.RegisterActivity`, nhưng thư mục `presentation/auth/` **không tồn tại**.
Manifest merger không kiểm tra class có thật hay không nên app vẫn build được, nhưng đây là rác
còn lại từ giai đoạn Firebase (đã gỡ). Giám khảo đọc manifest sẽ thấy ngay.

→ **Xoá** hai block `<activity>` này.

### A2. Xin quyền vị trí nhưng không bao giờ dùng
`AndroidManifest.xml:7-8` khai báo `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION`.
Tìm toàn bộ source: **không có** `LocationManager`, `FusedLocationProviderClient`,
hay `requestPermissions` nào. Quyền thừa = điểm trừ về bảo mật/quyền riêng tư.

Có 2 hướng, chọn 1:
- **Xoá** 2 dòng permission (nhanh, an toàn), **hoặc**
- **Dùng thật**: nút "Dùng vị trí hiện tại" ở `PlannerActivity` để điền `start_address`.
  Rubric của giảng viên ghi rõ *"demonstrate ... runtime permissions where applicable"* →
  làm cái này sẽ **ăn thêm điểm** ở cả Functional completeness lẫn Originality.
  Đây là hạng mục đáng làm nhất nếu chỉ chọn được một việc.

### A3. `RemoteImageLoader` có thể gán nhầm ảnh cho item RecyclerView
`infrastructure/remote/RemoteImageLoader.java:26-31`: nhánh cache-hit `return` sớm mà
**không cập nhật `target.setTag(url)`**. Kịch bản lỗi trong `PlaceAdapter`:

1. Bind item A → chưa có cache → `setTag(urlA)`, tải nền.
2. Người dùng cuộn, cùng `ImageView` được bind lại cho item B → B có cache → set bitmap B, tag vẫn là `urlA`.
3. Request A xong → `url.equals(target.getTag())` đúng → **đè ảnh A lên item B**.

→ Sửa 1 dòng: `target.setTag(url);` đặt **trước** khi kiểm tra cache.

Kèm theo: `LruCache<>(24)` đang giới hạn theo **số lượng ảnh**, không theo bộ nhớ.
24 ảnh bitmap lớn có thể vài chục MB. Nên override `sizeOf()` trả về
`bitmap.getByteCount() / 1024` và đặt maxSize theo `Runtime.maxMemory()/8`.

### A4. Xoay màn hình làm mất trạng thái ở 2 màn hình quan trọng

**`MainActivity`** (`presentation/MainActivity.java:39-41, 76-90`):
khi xoay, FragmentManager tự phục hồi fragment cũ, nhưng `BottomNavigationView` cũng khôi phục
item đã chọn → listener bắn → `replace()` bằng **fragment mới tinh**. Kết quả: HomeFragment
gọi lại API thời tiết mỗi lần xoay, vị trí cuộn mất.
→ Chỉ tạo fragment mới khi `savedInstanceState == null` hoặc khi item **thực sự đổi**
(so sánh với fragment đang hiển thị trước khi replace).

**`ItineraryActivity`** (`presentation/itinerary/ItineraryActivity.java:84-95`):
`itinerary` chỉ đọc từ `getIntent()`. Người dùng bấm "Thay đổi" đổi vài điểm
(`ItineraryEditor.replace`) rồi xoay máy → **toàn bộ chỉnh sửa biến mất**, quay về bản gốc,
và chip ngày reset về Ngày 1.
→ Lưu `itinerary` và `selectedDay`/`pendingDayNumber` vào `onSaveInstanceState`,
đọc lại trong `onCreate` (model đã `Serializable` sẵn nên rất nhanh),
hoặc tốt hơn: đưa vào `ViewModel` (xem B1).

Đây là mục rubric nói thẳng: *"demonstrate lifecycle handling"*. Giám khảo gần như chắc chắn
sẽ xoay màn hình khi chấm.

### A5. Rò rỉ thread — mỗi lần bấm nút tạo một ExecutorService mới
`ItineraryActivity.java:101` gọi `new RoomSavedTripRepository(this)` **trong onClick**, mà
constructor của nó (`RoomSavedTripRepository.java:25`) tạo `Executors.newSingleThreadExecutor()`.
Tương tự `HomeFragment.java:48` (`new RemoteWeatherRepository()` mỗi lần retry) và
`ItineraryActivity.java:270` (`new RemoteItineraryShareRepository(...)` mỗi lần xuất PDF).
Thread pool không bao giờ được `shutdown()` → mỗi lần bấm là một thread sống mãi.

→ Tạo repository **một lần** trong `onCreate` (field), hoặc tốt hơn: dùng một
`ExecutorService` dùng chung toàn app (một class `AppExecutors` static, hoặc cung cấp qua
`ServiceLocator` ở B2). `ItineraryActivity.pdfExecutor` (dòng 61) cũng cần `shutdown()` trong `onDestroy`.

---

## B. KIẾN TRÚC — nâng "Technical quality (25%)" (🟡 P1)

### B1. Không có ViewModel/LiveData nào, dù đã khai báo dependency
`app/build.gradle.kts` có `lifecycle-viewmodel` và `lifecycle-livedata`, nhưng tìm cả source:
**0 class ViewModel, 0 LiveData**. Toàn bộ state nằm trong field của Activity/Fragment.
Đây là lý do gốc của A4, và là điểm yếu lớn nhất khi bảo vệ phần "kiến trúc" trong báo cáo.

→ Ưu tiên thêm **2 ViewModel** (không cần làm hết):
- `HomeViewModel` giữ `LiveData<WeatherSnapshot>` → thời tiết không tải lại khi xoay.
- `ItineraryViewModel` giữ `Itinerary` đang chỉnh sửa → sửa luôn A4.

Chỉ 2 file này đã đủ để nói "MVVM có thật" trong báo cáo, và chi phí khoảng 1–2 giờ.

### B2. Presentation import thẳng Infrastructure (vi phạm Clean Architecture)
17 chỗ trong `presentation/` import `...infrastructure...` — ví dụ
`PlaceSelectionActivity.java:24-27` tự tay dựng
`new ResilientPlannerRepository(new RemotePlannerRepository(...), new DemoPlannerRepository())`.
Mũi tên phụ thuộc lẽ ra phải là `presentation → domain ← infrastructure`, nhưng hiện tại
`presentation → infrastructure` trực tiếp.

→ Thêm một class `di/ServiceLocator.java` (khoảng 40 dòng, không cần Hilt):

```java
public final class ServiceLocator {
    public static PlannerRepository planner() { ... }
    public static CatalogRepository catalog(...) { ... }
    public static SavedTripRepository savedTrips(Context c) { ... }
}
```

Activity chỉ còn giữ kiểu **interface** của domain. Đây là thay đổi rẻ (đổi import + 1 dòng khởi tạo
mỗi màn hình) nhưng nói rất tốt trong phần kiến trúc của báo cáo.

### B3. `RemoteWeatherRepository` không có interface trong domain
Khác với planner/catalog/saved-trip, thời tiết không có `domain/repository/WeatherRepository.java`,
nên `HomeFragment.java:18` phải import thẳng class infrastructure.
→ Thêm interface `WeatherRepository` + (tuỳ chọn) `LoadWeatherUseCase`, để nhất quán với 3 repository kia.

### B4. Chỉ có 1 use case cho toàn bộ ứng dụng
`application/usecase/` chỉ có `GenerateItineraryUseCase`. Các luồng khác (lưu chuyến, tìm kiếm,
thay thế điểm dừng, tải thời tiết) gọi thẳng repository từ Activity.
→ Nếu còn thời gian, tách thêm `SaveTripUseCase`, `SearchPlacesUseCase`, `ReplaceStopUseCase`.
Rẻ về code, và làm sơ đồ kiến trúc trong báo cáo cân đối hơn nhiều.

### B5. Nhận diện emulator bằng `Build.FINGERPRINT` là cách "chữa cháy"
`RemotePlannerRepository.java:31-34, 43-53`: base URL được chọn bằng cách đoán thiết bị,
và IP LAN `192.168.1.10` được **hard-code trong source**. IP này đổi mỗi lần đổi Wi-Fi.
→ Chuyển sang `buildConfigField` trong `build.gradle.kts` (debug dùng LAN, release dùng HTTPS),
hoặc tối thiểu đưa vào một class `BackendConfig` duy nhất để chỉ sửa một chỗ.
**Bắt buộc trước khi nộp APK release:** APK phát hành không được trỏ vào `192.168.x.x`.

### B6. Lưu itinerary bằng Java Serialization vào cột BLOB
`RoomSavedTripRepository.java:73-83` ghi `ObjectOutputStream` của cả `Itinerary` vào Room.
Hệ quả (chính code cũng đã lường trước ở dòng 55-57, phải `catch` và **bỏ im lặng** chuyến đi cũ):
chỉ cần thêm/đổi một field trong `ItineraryStop` là **toàn bộ chuyến đã lưu của người dùng biến mất
không thông báo**.
→ Đúng nhất là lưu JSON (`org.json` đã có sẵn, không thêm thư viện) thay cho Serializable —
JSON bỏ qua field lạ thay vì vỡ. Nếu không kịp đổi, ít nhất **hiện Toast/dòng chữ**
cho người dùng biết có chuyến không đọc được, thay vì im lặng.

---

## C. UI/UX & hiệu năng (🟡 P1)

### C1. Màn hình lịch trình dựng view bằng tay, không dùng RecyclerView
`ItineraryActivity.renderDay()` (dòng 140-224, ~85 dòng) tạo `MaterialCardView`/`LinearLayout`/
`TextView` bằng code Java rồi `addView` hết vào một `LinearLayout`. Không có tái sử dụng view.
Chuyến 7 ngày × 6 điểm = 42 card cùng tồn tại, mỗi card kèm 1 ImageView tải mạng.
→ Chuyển sang `RecyclerView` + file `item_itinerary_stop.xml`. Vừa mượt hơn,
vừa giảm `ItineraryActivity` xuống còn khoảng một nửa số dòng.
Bạn **đã có sẵn** `PlaceAdapter` làm mẫu để copy cấu trúc.

### C2. Tab "Hồ sơ" chỉ là màn hình "Coming soon"
`ComingSoonFragment` + `strings.xml`: *"Tính năng đang được hoàn thiện"*.
Rubric yêu cầu 3–4 màn hình **kết nối có ý nghĩa**; một tab trống là điểm trừ UI/UX rõ rệt.
Chọn 1 trong 2:
- **Xoá** mục "Hồ sơ" khỏi `bottom_nav_menu.xml` (an toàn nhất — 3 tab thành 2, vẫn dư số màn hình
  vì Catalog/Planner/Selection/Itinerary/Map/Detail đều là màn hình thật), **hoặc**
- **Làm thật, mức tối thiểu, offline**: tên người dùng + ngôn ngữ ưu tiên + số chuyến đã lưu +
  nút xoá toàn bộ dữ liệu, lưu bằng `SharedPreferences`. Khoảng 1 giờ, và biến điểm trừ thành điểm cộng.

### C3. Không có chế độ tối (dark mode)
`values/themes.xml` không có bản `values-night/`. Máy đặt dark mode sẽ thấy giao diện lệch màu.
→ Thêm `values-night/themes.xml` (chỉ cần đảo màu nền/chữ). Rẻ, và rất "ăn ảnh" trong video demo.

### C4. Thiếu offline fallback ở màn hình danh mục và thay thế
`PlaceSelectionActivity` có `ResilientPlannerRepository` (remote → demo), nhưng
`CatalogActivity` và `ReplacementActivity` chỉ có `RemoteCatalogRepository`.
Django tắt → hai màn hình này chỉ hiện lỗi.
→ Cache danh mục lần tải gần nhất vào Room (hoặc `SharedPreferences` dạng JSON) và
hiển thị bản cache kèm nhãn "dữ liệu offline". Rubric ghi rõ *"explicit loading, empty, error,
and offline states"*.

### C5. Trạng thái rỗng/lỗi chưa phủ hết
- `HomeFragment`: có loading/lỗi/retry cho thời tiết ✅
- `CatalogActivity`, `PlaceSelectionActivity`: có empty + error ✅
- `MapActivity`, `PlaceDetailActivity`: **chưa** có trạng thái lỗi khi WebView/ảnh không tải được.
→ Bổ sung thông báo lỗi cho 2 màn này nếu còn thời gian.

### C6. Nút "back" là ký tự `‹` trong TextView
6 layout dùng `<TextView android:text="‹" android:textSize="36sp">` làm nút quay lại
(`activity_catalog.xml:10`, `activity_itinerary.xml:11`, `activity_map.xml:9`,
`activity_place_detail.xml:12`, `activity_place_selection.xml:10`, `activity_replacement.xml:10`).
Không có `contentDescription` → TalkBack đọc "less-than sign". Vùng chạm ổn (48dp) nhưng
không có hiệu ứng ripple.
→ Đổi sang `MaterialToolbar` với `navigationIcon` chuẩn, hoặc tối thiểu thêm
`android:contentDescription="@string/back"` + `android:background="?selectableItemBackgroundBorderless"`.

### C7. `strings.xml` viết dồn nhiều `<string>` trên một dòng
`values/strings.xml` và `values-en/strings.xml` nhồi 10–15 string mỗi dòng.
Không sai về kỹ thuật, nhưng rất khó review và khó merge khi nhiều người cùng sửa.
→ Format lại mỗi string một dòng (Android Studio: `Ctrl+Alt+L` trên file XML).
Việc này mất 10 giây và làm phần "chất lượng mã nguồn" nhìn chuyên nghiệp hơn hẳn.

### C8. `HomeFragment.loadWeather` bị nén thành 2 dòng khổng lồ
`presentation/home/HomeFragment.java:45-51`: toàn bộ `onSuccess` và `onError` được viết
thành **một dòng dài hơn 800 ký tự**, gồm parse ngày, format chuỗi, vòng lặp và try/catch.
Đây là chỗ khó bảo vệ nhất khi vấn đáp và cũng là chỗ dễ bị trừ điểm "chất lượng mã nguồn" nhất
trong cả project. `RemoteWeatherRepository.java:9-11` cũng bị nén tương tự (cả class HTTP + parse
JSON gói trong 3 dòng).
→ **Nên sửa** — tách thành các method có tên rõ ràng (`bindCurrent`, `bindForecast`, `showError`).
Đây là việc rẻ nhất trong cả danh sách này so với điểm thu được.

---

## D. Bảo mật & cấu hình phát hành (🟡 P1)

### D1. `usesCleartextTraffic="true"` cho toàn app
`AndroidManifest.xml:12`. Cần cho HTTP tới Django LAN khi dev, nhưng bật toàn cục nghĩa là
**mọi** kết nối HTTP không mã hoá đều được phép, kể cả Open-Meteo (vốn đã HTTPS).
→ Thay bằng `res/xml/network_security_config.xml` chỉ cho phép cleartext với
`10.0.2.2` và `192.168.1.10`, và chỉ trong build debug.

### D2. `applicationId` vẫn là `com.example.finalproject`
`app/build.gradle.kts:9`. `com.example.*` là namespace mẫu, Google Play còn chặn hẳn.
→ Đổi thành `com.journify.app` (hoặc theo tên nhóm). **Lưu ý:** đổi `applicationId` sẽ làm
app cài đè bị coi là app mới; đổi `namespace` thì phải rename package Java. Nếu ngại rủi ro sát
deadline, chỉ đổi `applicationId` là đủ và giữ nguyên `namespace`.

### D3. Chưa có cấu hình build release
`buildTypes.release` có `isMinifyEnabled = false` và **không có `signingConfig`**.
Rubric yêu cầu nộp `apk/app-release.apk`.
→ Tạo keystore, thêm `signingConfigs`, và bật `isMinifyEnabled = true` + `isShrinkResources = true`
(nhớ test lại: R8 có thể ảnh hưởng các model `Serializable` — nếu lỗi thì thêm rule
`-keep class com.example.finalproject.domain.model.** { *; }`).
Nếu gấp: cứ để `minify = false`, chỉ cần ký APK là đủ nộp.

### D4. Backend `DEBUG = True` cố định
`backend/firstsite/settings.py:31`. Nếu deploy public để QR trong PDF dùng được
(việc `JOURNIFY_MIGRATION.md` ghi là bước tiếp theo), `DEBUG=True` sẽ **phơi bày traceback,
đường dẫn file và biến môi trường** cho bất kỳ ai truy cập.
→ `DEBUG = os.environ.get('DJANGO_DEBUG', 'False') == 'True'`.

### D5. API mobile không có xác thực hay rate limit
`backend/home/mobile_api.py`: `mobile_generate_itinerary` và `mobile_create_itinerary_share`
đều `@csrf_exempt`, không auth. Chấp nhận được cho đồ án chạy LAN, nhưng nếu deploy public
thì bất kỳ ai cũng gọi được thuật toán tạo lịch trình (tốn CPU) và tạo token chia sẻ vô hạn.
→ Nếu deploy: thêm rate limit theo IP hoặc một API key tĩnh trong header.
Nếu không deploy: **ghi rõ trong báo cáo** rằng đây là giới hạn đã biết của bản đồ án —
tự nhận biết giới hạn được tính điểm ở phần "self-assessment".

---

## E. Có thể XOÁ (dọn dẹp, 🟢 P2 nhưng rất rẻ)

| Xoá gì | Ở đâu | Lý do |
|---|---|---|
| 2 block `<activity>` auth | `AndroidManifest.xml:38-45` | Class không tồn tại (A1) |
| 2 permission vị trí | `AndroidManifest.xml:7-8` | Không dùng (A2) — trừ khi làm A2 phương án 2 |
| `firebase-bom`, `firebase-auth`, `firebase-firestore`, `google-services` | `gradle/libs.versions.toml` | Firebase đã gỡ, comment `// Firebase (Removed)` còn trong build file |
| `navigation-fragment`, `navigation-ui` | `libs.versions.toml` + `build.gradle.kts` | **Không có nav graph nào**, app dùng Intent giữa Activity. Dependency thừa. |
| `espresso-core`, `ext-junit` | `build.gradle.kts` | Không có test instrumentation nào — hoặc xoá, hoặc viết 1 test (F1) |
| `constraintlayout` | `build.gradle.kts` | Kiểm tra lại: các layout hiện dùng LinearLayout/ScrollView |
| `continue_translation.py`, `fix_addresses.py`, `fix_highlight.py`, `fix_time_tags.py`, `LOAD_DATA_UPDATE_EXAMPLE.py` | `backend/` | Script chạy một lần, không thuộc runtime — làm rối thư mục `src/` khi nộp |
| `chatbot_vectors.pkl` | `backend/` | File nhị phân, luồng mobile không dùng (`JOURNIFY_MIGRATION.md` ghi có thể bỏ qua `sentence-transformers`) |
| `backend/home/Templates/` (cân nhắc) | `backend/` | Template web cũ; nếu bài nộp chỉ chấm mobile + API thì đây là nhiễu. **Kiểm tra `urls.py` trước khi xoá.** |

> Lưu ý về dependency thừa: xoá xong phải `assembleDebug` lại để chắc không có chỗ nào còn dùng.

---

## F. Có thể THÊM — theo thứ tự "điểm thu được / công sức bỏ ra"

| # | Việc | Công sức | Điểm rubric tác động |
|---|---|---|---|
| 1 | Sửa A1–A5 (lỗi thật) | 1–2 giờ | Technical quality, Functional completeness |
| 2 | Tách lại `HomeFragment.loadWeather` (C8) | 20 phút | Technical quality — rẻ nhất trong bảng |
| 3 | Nút "Dùng vị trí hiện tại" ở Planner (A2 pt.2) | 2 giờ | Runtime permission + Originality |
| 4 | `HomeViewModel` + `ItineraryViewModel` (B1) | 2 giờ | Technical quality, Lifecycle |
| 5 | `ServiceLocator` (B2) | 1 giờ | Technical quality (kiến trúc) |
| 6 | Dark mode `values-night/` (C3) | 45 phút | UI/UX + nổi bật trong video demo |
| 7 | Làm thật màn Hồ sơ, hoặc xoá tab (C2) | 1 giờ / 5 phút | UI/UX |
| 8 | Cache offline cho catalog (C4) | 1,5 giờ | Offline state (rubric ghi rõ) |
| 9 | `ItineraryActivity` → RecyclerView (C1) | 2 giờ | UI/UX, hiệu năng |
| 10 | Thêm unit test (F1 bên dưới) | 1 giờ | Technical quality |

### F1. Test hiện chỉ có 2 file
`ItineraryEditorTest` (1 test) và `WeatherCodeMapperTest`. Không có test cho
`GenerateItineraryUseCase` — mà đây là class **thuần Java, không phụ thuộc Android**, nên
test cực dễ. Chỉ cần một `FakePlannerRepository` là kiểm được cả 4 nhánh validation
(days, poi/ngày, budget, moods). Khoảng 40 dòng cho 5 test mới.
Con số "7 unit tests passing" trong báo cáo nhìn tốt hơn "2" rất nhiều.

### F2. Ý tưởng tính năng cho phần "Originality (15%)"
Chọn **một** thôi, đừng làm nhiều — chưa đầy 5 ngày tới deadline:
- **Gợi ý theo thời tiết**: đã có sẵn dữ liệu Open-Meteo 3 ngày → nếu ngày nào mưa > 60%,
  gợi ý hoán đổi điểm ngoài trời sang điểm trong nhà. Tận dụng dữ liệu đã có, rất "ăn" khi demo.
- **Xuất lịch trình sang Lịch điện thoại**: `Intent` tạo sự kiện calendar cho mỗi ngày.
  Rẻ, và là "device capability" đúng theo rubric.
- **So sánh 2 chuyến đã lưu** cạnh nhau (chi phí, số điểm, quãng đường).

---

## G. Chuẩn bị nộp bài (checklist — theo `JOURNIFY_MIGRATION.md`)

- [ ] Đổi base URL sang HTTPS đã deploy, **không** để `192.168.1.10` trong APK release (B5)
- [ ] Đặt `JOURNIFY_PUBLIC_BASE_URL` trước khi tạo QR, nếu không QR trong PDF **chỉ mở được trên emulator**
- [ ] `./gradlew clean` trước khi nén; loại `build/`, `.gradle/`, `.idea/`, `backend/.venv/`
- [ ] Cấu trúc: `<student_ids>/README.md`, `src/`, `apk/app-release.apk`, `report/report.pdf` (10–30 trang), `video/demo-link.txt`
- [ ] README ghi: thành viên + MSSV, tên đề tài, link demo, hướng dẫn build, thông tin đăng nhập nếu có
- [ ] Báo cáo credit thư viện: AndroidX, Material Components, Room, Django, OSRM, OpenStreetMap/Leaflet, Open-Meteo
- [ ] Video 5–10 phút, **tất cả thành viên đều phải nói**
- [ ] Cập nhật `JOURNIFY_MIGRATION.md`: đánh dấu M7 và ghi lại các thay đổi từ tài liệu này

---

## H. Những điểm project đang làm TỐT (nên nêu trong báo cáo)

Rà soát không chỉ tìm lỗi — mấy điểm dưới đây tốt hơn mặt bằng đồ án và nên được nói rõ
trong phần tự đánh giá:

- **Chống race condition trong tìm kiếm**: `CatalogActivity.java:161` và
  `PlaceSelectionActivity.java:204` dùng `requestVersion` + kiểm tra query/tab hiện tại,
  nên phản hồi cũ không bao giờ đè lên kết quả mới. Đây là lỗi rất phổ biến mà project này đã xử lý đúng.
- **Debounce 280ms** cho gõ phím, tránh spam request.
- **Song ngữ đúng cách**: dùng `AppCompatDelegate.setApplicationLocales` + `autoStoreLocales`,
  và gửi `language=en` cho backend thay vì dịch trên máy — đúng chuẩn Android hiện đại.
- **Chia sẻ file an toàn**: dùng `FileProvider` + thư mục cache, không xin quyền lưu trữ rộng.
- **Domain thuần Java**: `domain/` không import `android.*` — kiểm tra lại thấy đúng thật.
- **Fallback offline** cho luồng tạo lịch trình (`ResilientPlannerRepository`) — demo không chết khi rớt mạng.
- **Xuất PDF native** giữ được tiếng Việt có dấu, kèm QR resume 30 ngày.

---

## Tóm tắt: nếu chỉ còn 1 ngày, làm đúng 5 việc này

1. **A1 + A2** — xoá activity ma và permission thừa (10 phút)
2. **A3** — sửa 1 dòng `setTag` trong `RemoteImageLoader` (2 phút)
3. **A4** — `onSaveInstanceState` cho `ItineraryActivity` + sửa fragment recreate ở `MainActivity` (45 phút)
4. **C8** — tách lại `HomeFragment.loadWeather` cho đọc được (20 phút)
5. **C2** — xoá tab "Hồ sơ" trống, hoặc làm bản tối giản (5 phút / 1 giờ)

Tổng dưới 2 giờ, và chạm vào cả 3 hạng mục chấm điểm nặng nhất.
