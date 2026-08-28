from django import forms
from django.contrib.auth.models import User
from django.contrib.auth.forms import PasswordChangeForm
from .models import Profile
from django.core.exceptions import ValidationError
import re
from django.db.models import Q

class UserUpdateForm(forms.ModelForm):
    email = forms.EmailField(required=False, widget=forms.EmailInput(attrs={'class': 'auth-form-input'}))
    
    class Meta:
        model = User
        fields = ['email'] # Username thường không cho sửa, nếu muốn sửa thì thêm 'username'

class ProfileUpdateForm(forms.ModelForm):
    phone_number = forms.CharField(required=False, widget=forms.TextInput(attrs={'class': 'auth-form-input'}))

    class Meta:
        model = Profile
        fields = ['phone_number', 'avatar']
        
class CustomPasswordChangeForm(PasswordChangeForm):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        # Duyệt qua tất cả các trường (Mật khẩu cũ, Mật khẩu mới...)
        for field in self.fields.values():
            # 1. Thêm class CSS để giao diện giống phần Tài khoản
            field.widget.attrs['class'] = 'auth-form-input'
            # 2. Xóa dòng gợi ý (help_text) gây rối mắt
            field.help_text = None
            
class ForgotPasswordForm(forms.Form):
    contact_info = forms.CharField(
        label="Email hoặc Số điện thoại",
        widget=forms.TextInput(attrs={
            'class': 'auth-form-input', 
            'placeholder': 'Nhập email hoặc số điện thoại'
        })
    )

    def clean_contact_info(self):
        data = self.cleaned_data.get('contact_info').strip()
        
        # Kiểm tra xem có phải email hay không (đơn giản)
        if '@' in data:
            user = User.objects.filter(email=data).first()
            if not user:
                raise ValidationError("Email này chưa được đăng ký.")
            self.user_cache = user
            self.contact_type = 'email'
        
        # Nếu không phải email, coi là số điện thoại
        else:
            # Validate số điện thoại (ví dụ: chỉ chứa số, dài 9-11 ký tự)
            if not re.match(r'^\d{9,11}$', data):
                raise ValidationError("Số điện thoại không hợp lệ.")
            
            # Tìm User thông qua Profile (OneToOne relationship)
            # Lưu ý: 'profile__phone_number' dựa trên model Profile bạn đã up
            user = User.objects.filter(profile__phone_number=data).first()
            if not user:
                raise ValidationError("Số điện thoại này chưa được đăng ký.")
            
            self.user_cache = user
            self.contact_type = 'phone'
            
        return data

class OTPVerificationForm(forms.Form):
    otp = forms.CharField(
        label="Mã xác nhận",
        max_length=6,
        widget=forms.TextInput(attrs={'class': 'auth-form-input', 'placeholder': 'Nhập mã 6 số', 'style': 'letter-spacing: 4px; text-align: center;'})
    )

class ResetPasswordForm(forms.Form):
    new_password = forms.CharField(
        label="Mật khẩu mới",
        widget=forms.PasswordInput(attrs={'class': 'auth-form-input', 'placeholder': 'Nhập mật khẩu mới'})
    )
    confirm_password = forms.CharField(
        label="Xác nhận mật khẩu",
        widget=forms.PasswordInput(attrs={'class': 'auth-form-input', 'placeholder': 'Nhập lại mật khẩu'})
    )

    def clean(self):
        cleaned_data = super().clean()
        p1 = cleaned_data.get('new_password')
        p2 = cleaned_data.get('confirm_password')
        if p1 and p2 and p1 != p2:
            raise ValidationError("Mật khẩu xác nhận không khớp.")
        return cleaned_data