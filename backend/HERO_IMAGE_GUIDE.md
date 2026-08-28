# 📸 Hướng Dẫn Tối Ưu Ảnh Hero Section

## 🎯 **Tóm tắt nhanh**
- **Tỉ lệ khung hình**: 16:9 (1920×1080) hoặc 21:9 (2560×1080) 
- **Định dạng**: JPG (chất lượng 85-90%)
- **Kích thước file**: 300-800 KB mỗi ảnh
- **Vị trí lưu**: `home/static/home/images/`
- **Tên file**: `dalat.jpg`, `danang.jpg`, `hue.jpg`, `vungtau.jpg`

---

## 📐 **1. Kích thước & Tỉ lệ Ảnh**

### **Tỉ lệ khung hình (Aspect Ratio)**
Hero section được thiết kế cho **landscape orientation**:

| Tỉ lệ | Kích thước đề xuất | Phù hợp cho |
|-------|-------------------|-------------|
| **16:9** ⭐ | 1920×1080px | Desktop + Mobile (tốt nhất) |
| **21:9** | 2560×1080px | Ultrawide monitors |
| 16:10 | 1920×1200px | MacBook displays |

**✅ Khuyến nghị: 16:9 (1920×1080px)**
- Tối ưu cho mọi thiết bị
- Hỗ trợ Retina với 2x = 3840×2160px

### **Kích thước file**
```
Ảnh hiện tại của bạn:
- dalat.jpg:    1.4 MB ❌ (quá lớn, làm chậm trang)
- dalat_2.jpg:  3.1 MB ❌❌ (rất lớn!)
- danang.jpg:   500 KB ✅ (tốt)
- hue.jpg:      1.8 MB ❌
- vungtau.jpg:  1.3 MB ❌

Mục tiêu: 300-800 KB mỗi ảnh ✅
```

---

## 🖼️ **2. Quy Tắc Chọn Ảnh**

### **Composition (Bố cục)**
✅ **Nên:**
- Điểm nhấn ở **giữa hoặc lower-third** (để chữ không che mất)
- Sky/trời ở **phía trên** (nơi gradient nhạt nhất)
- Details/chi tiết ở **phía dưới** (nơi có gradient tối)
- Không gian rộng cho text overlay

❌ **Tránh:**
- Ảnh có text/chữ sẵn
- Ảnh quá đông người
- Ảnh có vật cản ở giữa khung
- Ảnh quá tối hoặc quá sáng

### **Màu sắc**
✅ **Lý tưởng:**
- Màu ấm (vàng, cam, đỏ) - tạo cảm giác mời gọi
- Trời xanh trong - tương phản tốt với chữ trắng
- Ánh sáng golden hour (sunrise/sunset)

⚠️ **Cẩn thận:**
- Ảnh full trắng (khó đọc chữ)
- Ảnh full đen (mất chi tiết)

### **Chất lượng kỹ thuật**
✅ **Cần có:**
- Độ phân giải cao (ít nhất 1920px chiều rộng)
- Sharp/rõ nét (không bị blur/mờ)
- Ít noise/nhiễu
- Chụp bằng DSLR/mirrorless hoặc flagship phone

---

## 🛠️ **3. Cách Tối Ưu Ảnh**

### **Bước 1: Resize về đúng kích thước**

**Dùng Photoshop:**
```
File → Export → Export As...
- Width: 1920px
- Height: 1080px
- Resample: Bicubic Sharper (for reduction)
```

**Dùng Online Tools:**
- [Squoosh.app](https://squoosh.app/) ⭐ (Google)
- [TinyPNG](https://tinypng.com/)
- [Compressor.io](https://compressor.io/)

**Dùng Command Line (ImageMagick):**
```bash
# Resize + optimize
magick input.jpg -resize 1920x1080^ -gravity center -extent 1920x1080 -quality 85 output.jpg
```

### **Bước 2: Optimize compression**

**JPG Quality Settings:**
```
95-100 = Quá lớn, không cần thiết
85-90  = ✅ Tối ưu (sweet spot)
75-80  = Chấp nhận được
<70    = Quá xấu, có artifacts
```

**Squoosh.app Settings (Khuyến nghị):**
```
Resize:
  Width: 1920
  Height: 1080
  Method: Lanczos3 (best quality)

Compress:
  Format: MozJPEG
  Quality: 85
  Chroma subsampling: 4:2:0
  Progressive: Yes ✅
  
Target: 300-800 KB
```

### **Bước 3: Tăng độ sắc nét (Optional)**

Nếu ảnh hơi mềm sau khi resize:
```
Photoshop:
  Filter → Sharpen → Unsharp Mask
  Amount: 80-120%
  Radius: 1.0-1.5 pixels
  Threshold: 0-2 levels

Hoặc:
  Filter → Sharpen → Smart Sharpen
  Amount: 100%
  Radius: 1.0
  Reduce Noise: 10%
```

---

## 📁 **4. Cách Đặt Tên & Lưu File**

### **Quy tắc đặt tên**
```
✅ Đúng:
  dalat.jpg
  danang.jpg
  hue.jpg
  vungtau.jpg

❌ Sai:
  Da Lat.jpg          (có space)
  dalat-hero.jpg      (không cần suffix)
  DALAT.JPG           (uppercase)
  dalat_final_v2.jpg  (quá dài)
```

### **Folder structure**
```
firstsite/
└── home/
    └── static/
        └── home/
            └── images/
                ├── dalat.jpg      ← Hero slides
                ├── danang.jpg
                ├── hue.jpg
                ├── vungtau.jpg
                ├── dalat_2.jpg    ← Backup (nếu cần)
                ├── pois/          ← POI thumbnails
                └── eateries/      ← Eatery thumbnails
```

### **Cách thay ảnh mới**

**Phương pháp 1: Overwrite trực tiếp**
```powershell
# Backup ảnh cũ
Copy-Item "home/static/home/images/dalat.jpg" "home/static/home/images/dalat_old.jpg"

# Copy ảnh mới (đã optimize) vào
Copy-Item "path/to/new/dalat_optimized.jpg" "home/static/home/images/dalat.jpg"

# Clear browser cache: Ctrl+Shift+R
```

**Phương pháp 2: Versioning (nếu cache vẫn còn)**
```html
<!-- Trong welcome.html, thêm ?v=2 -->
background-image: url("{% static 'home/images/dalat.jpg?v=2' %}");
```

---

## 🎨 **5. Gợi Ý Nguồn Ảnh**

### **Free Stock Photos (High Quality)**
1. **Unsplash** → [unsplash.com](https://unsplash.com/)
   - Tìm: "da lat vietnam", "vietnam landscape"
   - License: Free to use

2. **Pexels** → [pexels.com](https://www.pexels.com/)
   - Nhiều ảnh Đà Lạt chất lượng cao
   - License: Free commercial use

3. **Pixabay** → [pixabay.com](https://pixabay.com/)
   - Ảnh Vietnam đẹp
   - License: Free

### **Paid Stock (Premium Quality)**
- **Adobe Stock** (cao cấp nhất)
- **Shutterstock**
- **Getty Images**

### **Tự chụp**
Nếu bạn đi Đà Lạt:
- Dùng camera/phone tốt
- Chụp landscape orientation
- Chụp ở golden hour (6-7am hoặc 5-6pm)
- Chụp RAW để edit linh hoạt

---

## ⚡ **6. Checklist Trước Khi Upload**

```
☐ Kích thước: 1920×1080px (16:9)
☐ Format: JPG
☐ Quality: 85-90%
☐ File size: 300-800 KB
☐ File name: lowercase, no spaces
☐ Composition: Trời ở trên, chi tiết ở dưới
☐ Sharp: Không bị blur
☐ Color: Đủ sáng, tương phản tốt
☐ Location: home/static/home/images/
☐ Test: Clear cache & reload
```

---

## 🔍 **7. Test & Validate**

### **Sau khi upload ảnh mới:**

1. **Clear Django static cache:**
```powershell
python manage.py collectstatic --clear --noinput
```

2. **Hard refresh browser:**
```
Chrome/Edge: Ctrl + Shift + R
Firefox: Ctrl + F5
Safari: Cmd + Shift + R
```

3. **Check performance:**
- Open DevTools (F12)
- Network tab → Filter: Img
- Kiểm tra:
  - Load time < 1s
  - File size 300-800 KB
  - No 404 errors

4. **Test trên nhiều devices:**
```
Desktop:  1920×1080, 2560×1440
Tablet:   1024×768, 1366×768
Mobile:   375×667 (iPhone), 414×896 (iPhone XR)
```

---

## 📊 **8. Ví Dụ Cụ Thể**

### **Ảnh hiện tại vs Ảnh tối ưu**

```
❌ TRƯỚC (dalat.jpg):
  Kích thước: 2048×1365 (3:2 ratio)
  File size: 1.4 MB
  Load time: ~2s (slow 3G)
  
✅ SAU (dalat.jpg optimized):
  Kích thước: 1920×1080 (16:9 ratio)
  File size: 450 KB
  Load time: ~0.6s (slow 3G)
  Quality: 85%
  
Cải thiện: 68% nhẹ hơn, load nhanh hơn 3x!
```

---

## 🚀 **9. Advanced: Responsive Images**

Nếu muốn tối ưu hơn nữa cho mobile:

```html
<!-- Thêm vào future update -->
<picture>
  <source media="(max-width: 768px)" 
          srcset="{% static 'home/images/dalat-mobile.jpg' %}">
  <source media="(min-width: 769px)" 
          srcset="{% static 'home/images/dalat.jpg' %}">
  <img src="{% static 'home/images/dalat.jpg' %}" alt="Da Lat">
</picture>
```

Tạo thêm version mobile (1080×1920 portrait cho mobile).

---

## 📝 **10. Quick Commands Reference**

### **Resize batch nhiều ảnh cùng lúc:**

**PowerShell (Windows):**
```powershell
# Install ImageMagick first
# Resize tất cả JPG về 1920×1080
Get-ChildItem *.jpg | ForEach-Object {
    magick $_.Name -resize 1920x1080^ -gravity center -extent 1920x1080 -quality 85 "optimized_$($_.Name)"
}
```

**Bash (Mac/Linux):**
```bash
# Resize all JPGs
for img in *.jpg; do
    magick "$img" -resize 1920x1080^ -gravity center -extent 1920x1080 -quality 85 "optimized_$img"
done
```

---

## 🎯 **Tóm tắt cho người bận**

1. Download ảnh 1920×1080 từ Unsplash/Pexels
2. Vào [squoosh.app](https://squoosh.app/)
3. Upload ảnh → Resize 1920×1080 → Quality 85 → Download
4. Rename thành `dalat.jpg`, `danang.jpg`, etc.
5. Copy vào `home/static/home/images/`
6. Hard refresh: Ctrl+Shift+R

**Done! 🎉**

---

## ❓ **FAQ**

**Q: Tại sao ảnh vẫn bị mờ sau khi upload?**
A: Có thể do:
- Browser cache cũ → Clear cache (Ctrl+Shift+R)
- CSS filter brightness/contrast quá cao
- Ảnh gốc đã bị blur

**Q: Có cần ảnh 4K (3840×2160) không?**
A: Không cần thiết cho web. 1920×1080 là đủ, trừ khi:
- Target audience có nhiều Retina displays
- Website chạy trên TV/large screens

**Q: WebP có tốt hơn JPG không?**
A: WebP nhẹ hơn 25-35% nhưng:
- Safari cũ không support
- Django static serving cần config thêm
- JPG 85% quality là đủ tốt

**Q: Làm sao biết ảnh có tương phản tốt với text?**
A: Test bằng cách:
- Overlay chữ trắng lên ảnh
- Nếu đọc khó → Cần ảnh tối hơn hoặc tăng gradient overlay

---

**Chúc bạn có những ảnh hero đẹp! 🚀✨**
