import pickle
import os
import numpy as np
from .models import Poi, Eatery
import logging
import threading
from django.conf import settings

# Try importing optional AI dependencies with graceful fallback
try:
    from sentence_transformers import SentenceTransformer, util
    import torch
    HAS_SENTENCE_TRANSFORMERS = True
except ImportError:
    logging.warning("sentence-transformers not installed. AI features will be disabled.")
    HAS_SENTENCE_TRANSFORMERS = False

try:
    import google.generativeai as genai
    HAS_GENAI = True
except ImportError:
    logging.warning("google-generativeai not installed. AI features will be disabled.")
    HAS_GENAI = False

# Check if all required AI dependencies are available
AI_AVAILABLE = HAS_SENTENCE_TRANSFORMERS and HAS_GENAI

def _convert_chat_history_to_gemini_format(chat_history):
    """
    Convert various chat history formats to Gemini API compatible format.
    
    Input formats supported:
    1. [{'user': 'message', 'bot': 'response'}, ...]
    2. [{'role': 'user', 'parts': [{'text': 'message'}]}, {'role': 'model', 'parts': [{'text': 'response'}]}, ...]
    3. Empty list []
    
    Returns: List in Gemini format
    """
    if not chat_history:
        return []
    
    gemini_history = []
    
    for item in chat_history:
        if isinstance(item, dict):
            # Format 1: {'user': 'message', 'bot': 'response'}
            if 'user' in item and 'bot' in item:
                # Add user message
                gemini_history.append({
                    'role': 'user',
                    'parts': [{'text': str(item['user'])}]
                })
                # Add bot response
                gemini_history.append({
                    'role': 'model', 
                    'parts': [{'text': str(item['bot'])}]
                })
            
            # Format 2: Already in Gemini format
            elif 'role' in item and 'parts' in item:
                gemini_history.append(item)
            
            # Handle other potential formats
            elif 'message' in item and 'response' in item:
                gemini_history.append({
                    'role': 'user',
                    'parts': [{'text': str(item['message'])}]
                })
                gemini_history.append({
                    'role': 'model',
                    'parts': [{'text': str(item['response'])}]
                })
    
    return gemini_history

logging.basicConfig(level=logging.INFO)

gemini_model = None
model = None

knowledge_vectors = [] 
knowledge_data = []    
is_loaded = False
load_lock = threading.Lock() 

CACHE_FILE = "chatbot_vectors.pkl"

def _create_poi_search_document(poi):
    """Create a search document string for a POI."""
    parts = [poi.name]
    
    if poi.address:
        parts.append(poi.address)
    
    if poi.tags:
        parts.append(poi.tags)
    
    return " ".join(parts)

def _create_poi_formatted_answer(poi):
    """Create a formatted answer string for a POI."""
    answer = f"**{poi.name}**\n"
    
    if poi.address:
        answer += f"📍 Địa chỉ: {poi.address}\n"
    
    if poi.open_hours:
        answer += f"🕒 Giờ mở cửa: {poi.open_hours}\n"
    
    if poi.rating:
        answer += f"⭐ Đánh giá: {poi.rating}/5\n"
    
    if poi.price_per_person:
        answer += f"💰 Giá: {int(poi.price_per_person):,} VND/người\n"
    
    if poi.tags:
        answer += f"🏷️ Loại: {poi.tags}\n"
    
    if poi.tiktok_link:
        answer += f"📱 TikTok: {poi.tiktok_link}\n"
    
    return answer.strip()

def _create_eatery_search_document(eatery):
    """Create a search document string for an Eatery."""
    parts = [eatery.name]
    
    if eatery.address:
        parts.append(eatery.address)
    
    if eatery.time_tags:
        parts.append(eatery.time_tags)
    
    return " ".join(parts)

def _create_eatery_formatted_answer(eatery):
    """Create a formatted answer string for an Eatery."""
    answer = f"**{eatery.name}**\n"
    
    if eatery.address:
        answer += f"📍 Địa chỉ: {eatery.address}\n"
    
    if eatery.open_hours:
        answer += f"🕒 Giờ mở cửa: {eatery.open_hours}\n"
    
    if eatery.time_tags:
        answer += f"⏰ Thời gian phù hợp: {eatery.time_tags}\n"
    
    if eatery.rating:
        answer += f"⭐ Đánh giá: {eatery.rating}/5\n"
    
    # Format price range
    if eatery.price_min and eatery.price_max:
        if eatery.price_min == eatery.price_max:
            answer += f"💰 Giá: {eatery.price_min:,} VND\n"
        else:
            answer += f"💰 Giá: {eatery.price_min:,} - {eatery.price_max:,} VND\n"
    elif eatery.price_min:
        answer += f"💰 Giá từ: {eatery.price_min:,} VND\n"
    elif eatery.price_max:
        answer += f"💰 Giá tối đa: {eatery.price_max:,} VND\n"
    
    if eatery.tiktok_link:
        answer += f"📱 TikTok: {eatery.tiktok_link}\n"
    
    return answer.strip()

# def load_data_and_vectorize():
#     """
#     Tải và vector hóa dữ liệu từ CSDL.
#     Hàm này giờ đây cũng chịu trách nhiệm tải mô hình (chỉ 1 lần).
#     """
#     global knowledge_vectors, knowledge_data, is_loaded, model, gemini_model

#     with load_lock: 
        
#         # --- THAY ĐỔI: Chuyển khối cấu hình Gemini vào đây ---
#         if gemini_model is None:
#             try:
#                 genai.configure(api_key=settings.GEMINI_API_KEY)
#                 gemini_model = genai.GenerativeModel('gemini-2.0-flash')
#                 logging.info("Đã cấu hình Gemini API thành công.")
#             except Exception as e:
#                 logging.error(f"Lỗi khi cấu hình Gemini: {e}")
#                 gemini_model = None # Giữ là None nếu lỗi
#                 is_loaded = False
#                 return # Không thể tiếp tục nếu không có Gemini
        
#         # --- THAY ĐỔI: Chuyển khối tải mô hình AI vào đây ---
#         if model is None:
#             try:
#                 # Đảm bảo bạn dùng đúng mô hình bạn muốn
#                 model = SentenceTransformer('all-MiniLM-L6-v2') 
#                 logging.info("Tải mô hình AI thành công.")
#             except Exception as e:
#                 logging.error(f"Lỗi nghiêm trọng khi tải mô hình AI: {e}")
#                 model = None # Giữ là None nếu lỗi
#                 is_loaded = False
#                 return # Không thể tiếp tục nếu không có mô hình
        
#         # Nếu đã tải rồi thì không chạy lại phần vector hóa (trừ khi có logic xóa cache)
#         if is_loaded:
#              logging.info("Mô hình và dữ liệu đã được tải trước đó.")
#              return

#         logging.info("Bắt đầu quá trình vector hóa dữ liệu từ CSDL...")
        
#         temp_documents = []
#         temp_data = []      

#         # 1. Tải Locations
#         try:
#             locations = Location.objects.all()
#             for loc in locations:
#                 temp_documents.append(loc.to_search_document())
#                 temp_data.append({ "context_string": loc.get_formatted_answer() })
#             logging.info(f"Đã tải {len(locations)} địa điểm.")
#         except Exception as e:
#             logging.error(f"Lỗi khi tải Locations: {e}")

#         # 2. Tải Restaurants
#         try:
#             restaurants = Restaurant.objects.all()
#             for res in restaurants:
#                 temp_documents.append(res.to_search_document())
#                 temp_data.append({ "context_string": res.get_formatted_answer() })
#             logging.info(f"Đã tải {len(restaurants)} quán ăn.")
#         except Exception as e:
#             logging.error(f"Lỗi khi tải Restaurants: {e}")

#         # 3. Vector hóa tất cả
#         if temp_documents:
#             try:
#                 knowledge_vectors = model.encode(temp_documents, convert_to_tensor=True, show_progress_bar=True)
#                 knowledge_data = temp_data
#                 is_loaded = True 
#                 logging.info(f"ĐÃ VECTOR HÓA THÀNH CÔNG {len(knowledge_vectors)} MỤC KIẾN THỨC.")
#             except Exception as e:
#                 logging.error(f"Lỗi nghiêm trọng trong quá trình encode: {e}")
#                 is_loaded = False
#         else:
#             logging.warning("Không có dữ liệu nào trong CSDL để vector hóa.")
#             is_loaded = False # Đặt là False nếu không có dữ liệu

def load_data_and_vectorize():
    """
    Tải dữ liệu và vector. Nếu đã có file cache thì load từ file để nhanh hơn.
    """
    global knowledge_vectors, knowledge_data, is_loaded, model, gemini_model

    if not AI_AVAILABLE:
        logging.error("AI dependencies not available. Please install sentence-transformers and google-generativeai packages.")
        return False

    with load_lock:
        # 1. Cấu hình Gemini (Giữ nguyên logic cũ)
        if gemini_model is None:
            try:
                genai.configure(api_key=settings.GEMINI_API_KEY)
                gemini_model = genai.GenerativeModel('gemini-2.0-flash')
                logging.info("Đã cấu hình Gemini API thành công.")
            except Exception as e:
                logging.error(f"Lỗi khi cấu hình Gemini: {e}")
                return

        # 2. Tải model Embedding (Giữ nguyên logic cũ)
        if model is None:
            try:
                model = SentenceTransformer('all-MiniLM-L6-v2')
                logging.info("Tải mô hình AI thành công.")
            except Exception as e:
                logging.error(f"Lỗi nghiêm trọng khi tải mô hình AI: {e}")
                return

        if is_loaded:
            return

        # --- CẢI TIẾN: KIỂM TRA CACHE ---
        if os.path.exists(CACHE_FILE):
            logging.info(f"Tìm thấy file cache '{CACHE_FILE}'. Đang tải dữ liệu từ đĩa...")
            try:
                with open(CACHE_FILE, 'rb') as f:
                    cache_data = pickle.load(f)
                    knowledge_vectors = cache_data['vectors']
                    knowledge_data = cache_data['data']
                is_loaded = True
                logging.info("Đã tải dữ liệu từ Cache thành công! (Không cần encode lại)")
                return
            except Exception as e:
                logging.warning(f"File cache lỗi, sẽ tạo lại: {e}")

        # 3. Nếu không có cache, thực hiện Encode bình thường (Logic cũ)
        logging.info("Đang vector hóa dữ liệu mới từ CSDL...")
        temp_documents = []
        temp_data = []

        # Tải POIs & Eateries với metadata để phân loại
        try:
            pois = Poi.objects.all()
            for poi in pois:
                temp_documents.append(_create_poi_search_document(poi))
                temp_data.append({ 
                    "context_string": _create_poi_formatted_answer(poi),
                    "type": "poi"  # Thêm metadata để phân biệt
                })
            
            eateries = Eatery.objects.all()
            for eatery in eateries:
                temp_documents.append(_create_eatery_search_document(eatery))
                temp_data.append({ 
                    "context_string": _create_eatery_formatted_answer(eatery),
                    "type": "eatery"  # Thêm metadata để phân biệt
                })
                
        except Exception as e:
             logging.error(f"Lỗi khi đọc CSDL: {e}")

        if temp_documents:
            try:
                knowledge_vectors = model.encode(temp_documents, convert_to_tensor=True, show_progress_bar=True)
                knowledge_data = temp_data
                is_loaded = True
                
                # --- CẢI TIẾN: LƯU XUỐNG CACHE ---
                with open(CACHE_FILE, 'wb') as f:
                    pickle.dump({'vectors': knowledge_vectors, 'data': knowledge_data}, f)
                logging.info(f"Đã lưu cache vào '{CACHE_FILE}'.")
                
            except Exception as e:
                logging.error(f"Lỗi encode: {e}")
                is_loaded = False
        else:
            logging.warning("Không có dữ liệu để vector hóa.")
            
def _transform_query(user_query, chat_history):
    """
    [NÂNG CẤP] Dùng Gemini để tối ưu hóa và dịch câu hỏi sang Tiếng Việt.
    Hàm này luôn tối ưu hóa, kể cả khi không có lịch sử chat.
    """
    
    if not AI_AVAILABLE or gemini_model is None:
        logging.warning("Gemini model chưa sẵn sàng (có thể do lỗi cấu hình), không thể transform query.")
        return user_query
    
    try:
        # Định dạng lịch sử chat (nếu có)
        history_prompt = "Không có lịch sử."
        if chat_history:
            # Convert to proper format first
            formatted_history = _convert_chat_history_to_gemini_format(chat_history)
            if formatted_history:
                history_str_parts = []
                for turn in formatted_history[-6:]:  # Lấy 6 lượt gần nhất (3 cặp user-bot)
                    role = "User" if turn['role'] == 'user' else "Bot"
                    text = turn['parts'][0]['text'] if turn['parts'] else ''
                    history_str_parts.append(f"{role}: {text}")
                history_prompt = "\n".join(history_str_parts)

        # Prompt mới thông minh hơn
        prompt = f"""
        Bạn là một trợ lý tối ưu hóa truy vấn tìm kiếm cho CSDL du lịch Đà Lạt. CSDL này chứa các quán ăn và địa điểm.
        Cơ sở dữ liệu tìm kiếm vector chỉ hiểu Tiếng Việt.

        NHIỆM VỤ: Dựa vào lịch sử chat và câu hỏi mới của người dùng, hãy tạo ra một cụm từ truy vấn (query) bằng Tiếng Việt TỐI ƯU NHẤT để tìm kiếm ngữ nghĩa.

        QUY TẮC TỐI ƯU HÓA:
        1.  **Câu hỏi chung (Ví dụ: "ăn sáng ở đâu", "tìm quán cafe", "chỗ nào ngắm hoàng hôn?"):** Hãy mở rộng thành truy vấn mô tả.
            - "ăn sáng ở đâu" -> "quán ăn sáng ngon rẻ ở Đà Lạt"
            - "I want to find coffee" -> "quán cà phê đẹp ở Đà Lạt"
            - "địa điểm ngắm hoàng hôn" -> "địa điểm ngắm hoàng hôn đẹp ở Đà Lạt"
        2.  **Câu hỏi cụ thể (Ví dụ: "quán Vườn Mơ ở đâu", "thông tin Thác Datanla"):** Hãy trích xuất tên riêng.
            - "quán Vườn Mơ ở đâu" -> "quán Vườn Mơ"
            - "information about Datanla Waterfall" -> "Thác Datanla"
        3.  **Câu hỏi về món ăn (Ví dụ: "ăn bánh căn ở đâu", "bánh ướt lòng gà?"):** Hãy trích xuất tên món ăn.
            - "ăn bánh căn ở đâu" -> "món bánh căn"
            - "bánh ướt lòng gà?" -> "bánh ướt lòng gà"

        LỊCH SỬ CHAT:
        {history_prompt}

        CÂU HỎI MỚI CỦA NGƯỜI DÙNG (CÓ THỂ BẰNG TIẾNG ANH HOẶC VIỆT):
        "{user_query}"

        HÃY TRẢ LỜI CHỈ VÀ CHỈ CỤM TỪ TRUY VẤN TIẾNG VIỆT ĐÃ TỐI ƯU HÓA.
        TRUY VẤN TIẾNG VIỆT TỐI ƯU:
        """

        response = gemini_model.generate_content(prompt)
        transformed_query = response.text.strip().replace("*", "") # Xóa ký tự markdown
        
        if not transformed_query or len(transformed_query) < 2:
             logging.warning("Transform query trả về rỗng, dùng query gốc.")
             return user_query 

        return transformed_query
    
    except Exception as e:
        logging.error(f"Lỗi khi transform query: {e}. Dùng query gốc.")
        return user_query

def _is_general_knowledge_query(query):
    """
    [MỚI] Dùng Gemini để phân loại ý định của người dùng.
    """
    if not AI_AVAILABLE or gemini_model is None:
        return False # Mặc định là 'SPECIFIC' nếu Gemini lỗi

    try:
        prompt = f"""
        Bạn là một bộ phân loại ý định. Câu hỏi của người dùng là về Đà Lạt.
        Hãy phân loại câu hỏi sau thành một trong hai loại:
        1.  **SPECIFIC:** Nếu câu hỏi đang tìm một địa điểm, quán ăn, hoặc cửa hàng cụ thể (ví dụ: "quán Vườn Mơ ở đâu", "tìm quán bánh căn", "địa điểm Phường 3").
        2.  **GENERAL:** Nếu câu hỏi là một câu hỏi kiến thức chung (ví dụ: "thời tiết Đà Lạt thế nào", "lịch sử Đà Lạt", "từ đây tới đó bao xa", "nên đi đâu vào buổi tối").

        Câu hỏi: "{query}"

        HÃY TRẢ LỜI CHỈ BẰNG MỘT TỪ: 'SPECIFIC' hoặc 'GENERAL'.
        PHÂN LOẠI:
        """
        response = gemini_model.generate_content(prompt)
        classification = response.text.strip().upper()
        
        if classification == "GENERAL":
            logging.info(f"Phân loại ý định cho '{query}': GENERAL")
            return True
        else:
            logging.info(f"Phân loại ý định cho '{query}': SPECIFIC")
            return False
            
    except Exception as e:
        logging.error(f"Lỗi khi phân loại ý định: {e}")
        return False # Mặc định là SPECIFIC (an toàn hơn)
     
# def find_best_answer(user_query, chat_history, similarity_threshold=0.50):
#     global model, knowledge_vectors, knowledge_data, is_loaded, gemini_model

#     if not is_loaded or model is None or gemini_model is None:
#          logging.warning("AI chưa sẵn sàng (model hoặc data chưa load), đang kích hoạt tải...")
#          load_data_and_vectorize() # Kích hoạt tải (đã có khóa an toàn)
         
#          # Kiểm tra lại sau khi tải
#          if not is_loaded or model is None or gemini_model is None:
#              logging.error("Tải AI thất bại, kiểm tra log lỗi ở trên.")
#              # Trả về lỗi dựa trên cái gì bị thiếu
#              if gemini_model is None:
#                  return "Lỗi kết nối đến dịch vụ AI (Gemini). Vui lòng kiểm tra API Key hoặc lỗi 429."
#              if model is None:
#                  return "Lỗi không thể tải mô hình vector hóa. Vui lòng kiểm tra tên mô hình."
#              return "Xin lỗi, bộ não AI của tôi đang được bảo trì. Vui lòng thử lại sau giây lát."
    
#     try:
#         # Bước 1: Tối ưu hóa và dịch query
#         transformed_query = _transform_query(user_query, chat_history)
        
#         logging.info(f"Query gốc (Ngôn ngữ gốc): '{user_query}' -> Query tìm kiếm (VI): '{transformed_query}'")

#         # Bước 2: Tìm kiếm vector
#         query_vector = model.encode(transformed_query, convert_to_tensor=True)
#         cos_scores = util.cos_sim(query_vector, knowledge_vectors)[0]
#         cos_scores_cpu = cos_scores.cpu()
        
#         top_k = 10
#         top_k_scores, top_k_indices_tensor = torch.topk(cos_scores_cpu, top_k)

#         relevant_indices = []
#         relevant_scores = []
#         for i in range(len(top_k_scores)):
#             score = top_k_scores[i].item()
#             if score >= similarity_threshold:
#                relevant_indices.append(top_k_indices_tensor[i].item())
#                relevant_scores.append(score)
#             else:
#                break 

#         logging.info(f"Query (VI): '{transformed_query}' -> Relevant indices: {relevant_indices}, Scores: {relevant_scores}")
        
#         chat_session = gemini_model.start_chat(history = chat_history)
        
#         if relevant_indices:
#             # [NÂNG CẤP] Prompt RAG khi TÌM THẤY context
#             context_parts = []
#             for idx in relevant_indices:
#                 matched_data = knowledge_data[idx]
#                 context_parts.append(matched_data["context_string"])
            
#             context_string = "\n\n---\n\n".join(context_parts)

#             prompt = f"""
#             [PERSONA]
#             Bạn là "Journify", một 'thổ địa' Đà Lạt chính hiệu. Giọng văn của bạn phải cực kỳ vui vẻ, thân thiện, nhiệt tình và am hiểu (như một người bạn đang giới thiệu cho bạn bè).

#             [THÔNG TIN TÌM ĐƯỢC]
#             Dưới đây là {len(context_parts)} kết quả bạn tìm thấy trong CSDL:
#             ---
#             {context_string}
#             ---

#             Câu hỏi gốc của người dùng: "{user_query}"
#             Chủ đề (đã dịch/tối ưu): "{transformed_query}"

#             YÊU CẦU BẮT BUỘC:
#             1.  **Ngôn ngữ:** Trả lời bằng CHÍNH XÁC ngôn ngữ của "Câu hỏi gốc".
#             2.  **Nội dung:** CHỈ sử dụng "Thông tin tìm được" để trả lời.
#             3.  **Tổng hợp:** Nếu có nhiều kết quả (ví dụ: nhiều quán ăn sáng), hãy tổng hợp chúng lại thành một danh sách thân thiện. (Ví dụ: "Bạn ơi, mình tìm thấy vài quán ăn sáng hay lắm nè:", sau đó liệt kê). Đừng chỉ trả lời 1 quán.
#             4.  **Links:** LUÔN LUÔN đính kèm link (Media/TikTok) nếu có trong thông tin.
#             5.  **Trung thực:** Nếu người dùng hỏi một chi tiết (ví dụ: 'có chỗ đậu xe hơi không?', 'có view đẹp không?') mà "Thông tin tìm được" không ghi rõ, hãy nói thật (ví dụ: "mình không có thông tin về chỗ đậu xe của quán X") nhưng VẪN giới thiệu quán đó.
#             6.  **Lịch sử:** {chat_session.history}
#             7.  **Chống lặp:** TUYỆT ĐỐI KHÔNG giới thiệu lại các địa điểm/quán ăn đã có trong "Lịch sử". Chỉ tập trung vào các kết quả MỚI trong "[THÔNG TIN TÌM ĐƯỢC]".
#             """

#             try:
#                 response = chat_session.send_message(prompt)
#                 return response.text
#             except Exception as e:
#                 logging.error(f"Lỗi khi gọi Gemini API: {e}")
#                 return "Đã xảy ra lỗi khi cố gắng tạo câu trả lời, vui lòng thử lại."

#         else:
#             # [NÂNG CẤP] Prompt Fallback khi KHÔNG TÌM THẤY context
#             logging.info(f"Không tìm thấy context cho '{transformed_query}'. Chuyển sang general knowledge.")
            
#             prompt = f"""
#             Bạn là "Journify", một trợ lý du lịch AI vui vẻ, CHUYÊN GIA về Đà Lạt.
#             Nhiệm vụ của bạn là trả lời các câu hỏi liên quan đến Đà Lạt, hoặc từ chối một cách lịch sự.

#             Câu hỏi gốc của người dùng: "{user_query}"
#             Chủ đề (đã dịch/tối ưu): "{transformed_query}"

#             [YÊU CẦU BẮT BUỘC]
#             1.  **Ngôn ngữ:** Trả lời bằng CHÍNH XÁC ngôn ngữ của "Câu hỏi gốc".
#             2.  **Quy tắc 1 (Trong phạm vi):** Nếu chủ đề "{transformed_query}" CÓ liên quan đến Đà Lạt (ví dụ: thời tiết, khách sạn, đường đi, lịch sử Đà Lạt...), hãy trả lời bằng kiến thức chung của bạn.
#                 -> HÃY TRẢ LỜI NHƯ MỘT HƯỚNG DẪN VIÊN DU LỊCH: Cung cấp câu trả lời chi tiết, hấp dẫn, và hữu ích.
            
#             3.  **Quy tắc 2 (Ngoài phạm vi):** Nếu chủ đề KHÔNG liên quan đến Đà Lạt (ví dụ: hỏi về Hà Nội, Sài Gòn, Toán học...), HÃY TỪ CHỐI LỊCH SỰ.
#                 - Ví dụ từ chối (VI): "Dạ, mình là Journify, trợ lý chuyên về Đà Lạt nên mình không rõ thông tin này rồi. Bạn có muốn hỏi gì về Đà Lạt không ạ? 😊"
#                 - Ví dụ từ chối (EN): "Sorry, I'm Journify, a Da Lat specialist, so I don't have information on that. Can I help you with anything related to Da Lat? 😊"
#             """
            
#             try:
#                 response = chat_session.send_message(prompt)
#                 return response.text
#             except Exception as e:
#                 logging.error(f"Lỗi khi gọi Gemini API (fallback): {e}")
#                 return "Đã xảy ra lỗi khi cố gắng tạo câu trả lời, vui lòng thử lại."
           
#     except Exception as e:
#         logging.error(f"Lỗi khi tìm câu trả lời: {e}")
#         return "Đã xảy ra lỗi trong quá trình xử lý, vui lòng thử lại."

def find_best_answer(user_query, chat_history, similarity_threshold=0.50):
    global model, knowledge_vectors, knowledge_data, is_loaded, gemini_model

    if not AI_AVAILABLE:
        return "Xin lỗi, các tính năng AI hiện không khả dụng. Vui lòng liên hệ quản trị viên để cài đặt các gói phụ thuộc cần thiết."

    if not is_loaded or model is None or gemini_model is None:
         logging.warning("AI chưa sẵn sàng (model hoặc data chưa load), đang kích hoạt tải...")
         load_data_and_vectorize() # Kích hoạt tải (đã có khóa an toàn)
         
         # Kiểm tra lại sau khi tải
         if not is_loaded or model is None or gemini_model is None:
             logging.error("Tải AI thất bại, kiểm tra log lỗi ở trên.")
             # Trả về lỗi dựa trên cái gì bị thiếu
             if gemini_model is None:
                 return "Lỗi kết nối đến dịch vụ AI (Gemini). Vui lòng kiểm tra API Key hoặc lỗi 429."
             if model is None:
                 return "Lỗi không thể tải mô hình vector hóa. Vui lòng kiểm tra tên mô hình."
             return "Xin lỗi, bộ não AI của tôi đang được bảo trì. Vui lòng thử lại sau giây lát."
    
    try:
        # Bước 1: Tối ưu hóa và dịch query
        transformed_query = _transform_query(user_query, chat_history)
        
        logging.info(f"Query gốc (Ngôn ngữ gốc): '{user_query}' -> Query tìm kiếm (VI): '{transformed_query}'")

        # --- [SỬA ĐỔI BẮT ĐẦU TỪ ĐÂY] ---
        
        # Bước 2: Phân loại ý định (Gọi hàm mới)
        is_general_query = _is_general_knowledge_query(transformed_query)

        relevant_indices = []
        relevant_scores = []
        
        # Chỉ tìm kiếm vector nếu ý định là 'SPECIFIC'
        if not is_general_query:
            logging.info("Ý định là SPECIFIC -> Đang tìm kiếm vector...")
            
            # Bước 3: Tìm kiếm vector (Nếu cần)
            query_vector = model.encode(transformed_query, convert_to_tensor=True)
            cos_scores = util.cos_sim(query_vector, knowledge_vectors)[0]
            cos_scores_cpu = cos_scores.cpu()
            
            top_k = 7 # Giữ nguyên top_k = 10 của bạn
            top_k_scores, top_k_indices_tensor = torch.topk(cos_scores_cpu, top_k)

            # Thu thập kết quả với metadata
            results_with_metadata = []
            for i in range(len(top_k_scores)):
                score = top_k_scores[i].item()
                if score >= similarity_threshold:
                    idx = top_k_indices_tensor[i].item()
                    result_type = knowledge_data[idx].get("type", "unknown")
                    results_with_metadata.append({
                        "index": idx,
                        "score": score,
                        "type": result_type
                    })
                else:
                    break
            
            # Phân loại query để ưu tiên đúng loại kết quả
            query_lower = transformed_query.lower()
            poi_keywords = ["hồ", "thác", "đồi", "núi", "chùa", "địa điểm", "nơi", "đi chơi", "tham quan", "du lịch", "vườn", "khu", "công viên", "cafe", "cà phê"]
            eatery_keywords = ["ăn", "món", "nhà hàng", "bánh", "phở", "bún", "cơm", "đồ ăn", "thức ăn"]
            
            is_poi_query = any(keyword in query_lower for keyword in poi_keywords)
            is_eatery_query = any(keyword in query_lower for keyword in eatery_keywords)
            
            # Sắp xếp lại: Ưu tiên POI nếu hỏi về địa điểm, ưu tiên Eatery nếu hỏi về ăn uống
            if is_poi_query and not is_eatery_query:
                # Ưu tiên POI: POI trước, Eatery sau
                results_with_metadata.sort(key=lambda x: (0 if x["type"] == "poi" else 1, -x["score"]))
                logging.info("Phát hiện query về địa điểm -> Ưu tiên POI")
            elif is_eatery_query and not is_poi_query:
                # Ưu tiên Eatery: Eatery trước, POI sau
                results_with_metadata.sort(key=lambda x: (0 if x["type"] == "eatery" else 1, -x["score"]))
                logging.info("Phát hiện query về ăn uống -> Ưu tiên Eatery")
            else:
                # Không rõ ràng hoặc cả hai -> Sắp xếp theo score
                results_with_metadata.sort(key=lambda x: -x["score"])
                logging.info("Query không rõ ràng -> Sắp xếp theo điểm số")
            
            # Trích xuất lại indices và scores sau khi sắp xếp
            relevant_indices = [r["index"] for r in results_with_metadata]
            relevant_scores = [r["score"] for r in results_with_metadata]

            logging.info(f"Query (VI): '{transformed_query}' -> Relevant indices: {relevant_indices}, Scores: {relevant_scores}, Types: {[r['type'] for r in results_with_metadata]}")
        
        else:
            # Nếu ý định là 'GENERAL', bỏ qua tìm kiếm, giữ relevant_indices = [] (rỗng)
            logging.info("Ý định là GENERAL -> Bỏ qua tìm kiếm vector, chuyển thẳng sang fallback.")
        
        # --- [KẾT THÚC SỬA ĐỔI] ---

        # Convert chat history to Gemini format before creating chat session
        gemini_formatted_history = _convert_chat_history_to_gemini_format(chat_history)
        chat_session = gemini_model.start_chat(history=gemini_formatted_history)
        
        # Logic này giờ sẽ tự động đi đúng hướng
        if relevant_indices:
            # [NÂNG CẤP] Prompt RAG khi TÌM THẤY context
            context_parts = []
            for idx in relevant_indices:
                matched_data = knowledge_data[idx]
                context_parts.append(matched_data["context_string"])
            
            context_string = "\n\n---\n\n".join(context_parts)

            prompt = f"""
            [PERSONA]
            Bạn là "Journify", một 'thổ địa' Đà Lạt chính hiệu. Giọng văn của bạn phải cực kỳ vui vẻ, thân thiện, nhiệt tình và am hiểu (như một người bạn đang giới thiệu cho bạn bè).

            [THÔNG TIN TÌM ĐƯỢC]
            Dưới đây là {len(context_parts)} kết quả bạn tìm thấy trong CSDL:
            ---
            {context_string}
            ---

            Câu hỏi gốc của người dùng: "{user_query}"
            Chủ đề (đã dịch/tối ưu): "{transformed_query}"

            YÊU CẦU BẮT BUỘC:
            1.  **Ngôn ngữ:** Trả lời bằng CHÍNH XÁC ngôn ngữ của "Câu hỏi gốc".
            2.  **Nội dung:** CHỈ sử dụng "Thông tin tìm được" để trả lời.
            3.  **Đúng trọng tâm:** 
                - Nếu câu hỏi về MỘT địa điểm/quán cụ thể (ví dụ: "Hồ Xuân Hương ở đâu?", "thông tin về Thác Datanla") → CHỈ trả lời về địa điểm/quán ĐÓ thôi. KHÔNG thêm danh sách địa điểm khác trừ khi người dùng yêu cầu.
                - Nếu câu hỏi yêu cầu nhiều lựa chọn (ví dụ: "gợi ý quán ăn sáng", "địa điểm check-in đẹp") → Liệt kê 3-5 kết quả tốt nhất.
            4.  **Tổng hợp:** Khi liệt kê nhiều kết quả, hãy tổng hợp thành danh sách thân thiện. (Ví dụ: "Mình tìm thấy vài quán ăn sáng hay lắm nè:", sau đó liệt kê).
            5.  **Links:** LUÔN LUÔN đính kèm link (Media/TikTok) nếu có trong thông tin.
            6.  **Trung thực:** Nếu người dùng hỏi một chi tiết (ví dụ: 'có chỗ đậu xe hơi không?') mà "Thông tin tìm được" không ghi rõ, hãy nói thật nhưng VẪN giới thiệu địa điểm đó.
            7.  **Chống lặp:** TUYỆT ĐỐI KHÔNG giới thiệu lại các địa điểm/quán ăn đã có trong lịch sử chat. Chỉ tập trung vào các kết quả MỚI.
            
            VÍ DỤ:
            - Câu hỏi: "Hồ Xuân Hương ở đâu?" → CHỈ trả lời về Hồ Xuân Hương (địa chỉ, giờ mở cửa, đặc điểm). KHÔNG thêm danh sách quán ăn gần đó.
            - Câu hỏi: "Gợi ý quán cafe view đẹp" → Liệt kê 3-5 quán cafe từ kết quả tìm được.
            """

            try:
                response = chat_session.send_message(prompt)
                return response.text
            except Exception as e:
                logging.error(f"Lỗi khi gọi Gemini API: {e}")
                return "Đã xảy ra lỗi khi cố gắng tạo câu trả lời, vui lòng thử lại."

        else:
            # [NÂNG CẤP] Prompt Fallback khi KHÔNG TÌM THẤY context (hoặc khi là câu hỏi GENERAL)
            logging.info(f"Không tìm thấy context cho '{transformed_query}' HOẶC đây là câu hỏi GENERAL. Chuyển sang general knowledge.")
            
            # (Prompt Fallback của bạn giữ nguyên)
            prompt = f"""
            Bạn là "Journify", một trợ lý du lịch AI vui vẻ, CHUYÊN GIA về Đà Lạt.
            Nhiệm vụ của bạn là trả lời các câu hỏi liên quan đến Đà Lạt, hoặc từ chối một cách lịch sự.

            Câu hỏi gốc của người dùng: "{user_query}"
            Chủ đề (đã dịch/tối ưu): "{transformed_query}"

            [YÊU CẦU BẮT BUỘC]
            1.  **Ngôn ngữ:** Trả lời bằng CHÍNH XÁC ngôn ngữ của "Câu hỏi gốc".
            2.  **Quy tắc 1 (Trong phạm vi):** Nếu chủ đề "{transformed_query}" CÓ liên quan đến Đà Lạt (ví dụ: thời tiết, khách sạn, đường đi, lịch sử Đà Lạt...), hãy trả lời bằng kiến thức chung của bạn.
                -> HÃY TRẢ LỜI NHƯ MỘT HƯỚNG DẪN VIÊN DU LỊCH: Cung cấp câu trả lời chi tiết, hấp dẫn, và hữu ích.
            
            3.  **Quy tắc 2 (Ngoài phạm vi):** Nếu chủ đề KHÔNG liên quan đến Đà Lạt (ví dụ: hỏi về Hà Nội, Sài Gòn, Toán học...), HÃY TỪ CHỐI LỊCH SỰ.
                - Ví dụ từ chối (VI): "Dạ, mình là Journify, trợ lý chuyên về Đà Lạt nên mình không rõ thông tin này rồi. Bạn có muốn hỏi gì về Đà Lạt không ạ? 😊"
                - Ví dụ từ chối (EN): "Sorry, I'm Journify, a Da Lat specialist, so I don't have information on that. Can I help you with anything related to Da Lat? 😊"
            """
            
            try:
                response = chat_session.send_message(prompt)
                return response.text
            except Exception as e:
                logging.error(f"Lỗi khi gọi Gemini API (fallback): {e}")
                return "Đã xảy ra lỗi khi cố gắng tạo câu trả lời, vui lòng thử lại."
           
    except Exception as e:
        logging.error(f"Lỗi khi tìm câu trả lời: {e}")
        return "Đã xảy ra lỗi trong quá trình xử lý, vui lòng thử lại."