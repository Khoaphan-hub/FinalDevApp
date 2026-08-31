package com.example.finalproject.infrastructure.local.export;
import android.content.Context;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import com.example.finalproject.domain.model.*;
import com.example.finalproject.R;
import java.io.*;
import java.text.NumberFormat;
import java.util.Locale;

public final class ItineraryPdfExporter {
    private static final int W=595,H=842,M=42;
    public File export(Context context, Itinerary itinerary, Bitmap qr, String url) throws IOException {
        File dir=new File(context.getCacheDir(),"exports"); if(!dir.exists()&&!dir.mkdirs()) throw new IOException("Không thể tạo thư mục PDF");
        File file=new File(dir,"Journify-itinerary-"+System.currentTimeMillis()+".pdf"); PdfDocument doc=new PdfDocument(); Writer w=new Writer(doc,context); w.newPage();
        w.text("JOURNIFY",12,true,Color.rgb(30,106,87)); w.gap(10); w.text(itinerary.getTitle(),25,true,Color.rgb(31,45,41)); w.gap(4); w.text(context.getString(R.string.pdf_subtitle),11,false,Color.DKGRAY); w.gap(12);
        w.box(context.getString(R.string.pdf_total_budget),money(itinerary.getTotalBudgetVnd()),context.getString(R.string.pdf_estimated_cost),money(itinerary.getEstimatedCostVnd())); w.gap(16);
        for(ItineraryDay day:itinerary.getDays()){w.ensure(44);w.text(context.getString(R.string.pdf_day,day.getDayNumber()),16,true,Color.rgb(30,106,87));w.gap(5);int index=1;for(ItineraryStop stop:day.getStops()){String prefix=stop.getType()==ItineraryStop.Type.ACCOMMODATION?context.getString(R.string.pdf_start):String.valueOf(index++);w.ensure(62);w.text(prefix+". "+safe(stop.getName()),12,true,Color.rgb(31,45,41));if(!safe(stop.getAddress()).isEmpty())w.text(safe(stop.getAddress()),9,false,Color.DKGRAY);if(stop.getMealSlot()!=null&&!stop.getMealSlot().isEmpty())w.text(context.getString(R.string.pdf_meal_slot,stop.getMealSlot()),9,false,Color.rgb(30,106,87));if(stop.getTravelToNextKm()>0)w.text(context.getString(R.string.pdf_next_distance,stop.getTravelToNextKm()),9,false,Color.GRAY);w.gap(8);}w.gap(7);}
        w.ensure(245);w.text(context.getString(R.string.pdf_reopen),15,true,Color.rgb(30,106,87));w.text(context.getString(R.string.pdf_qr_help),10,false,Color.DKGRAY);w.gap(10);if(qr!=null){Rect src=new Rect(0,0,qr.getWidth(),qr.getHeight());RectF dst=new RectF(M,w.y,M+150,w.y+150);w.canvas.drawBitmap(qr,src,dst,null);w.y+=158;}w.text(url,8,false,Color.GRAY);w.finish();try(FileOutputStream out=new FileOutputStream(file)){doc.writeTo(out);}doc.close();return file;
    }
    private static String safe(String v){return v==null?"":v;} private static String money(long v){return NumberFormat.getNumberInstance(Locale.getDefault()).format(Math.max(0,v))+" ₫";}
    private static final class Writer{final PdfDocument doc;final Context context;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);PdfDocument.Page page;Canvas canvas;int pageNo,y;Writer(PdfDocument d,Context c){doc=d;context=c;}void newPage(){if(page!=null)closePage();pageNo++;page=doc.startPage(new PdfDocument.PageInfo.Builder(W,H,pageNo).create());canvas=page.getCanvas();canvas.drawColor(Color.WHITE);y=M;}void closePage(){paint.setTextSize(8);paint.setColor(Color.GRAY);paint.setTypeface(Typeface.DEFAULT);canvas.drawText(context.getString(R.string.pdf_page,pageNo),M,H-22,paint);doc.finishPage(page);page=null;}void finish(){if(page!=null)closePage();}void gap(int p){y+=p;}void ensure(int h){if(y+h>H-45)newPage();}void text(String value,float size,boolean bold,int color){if(value==null)return;paint.setTextSize(size);paint.setColor(color);paint.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));float max=W-2*M;for(String paragraph:value.split("\\n",-1)){String line="";for(String word:paragraph.split(" ")){String test=line.isEmpty()?word:line+" "+word;if(paint.measureText(test)>max&&!line.isEmpty()){ensure((int)(size+7));canvas.drawText(line,M,y,paint);y+=size+5;line=word;}else line=test;}ensure((int)(size+7));canvas.drawText(line,M,y,paint);y+=size+5;}}void box(String a,String av,String b,String bv){ensure(74);paint.setColor(Color.rgb(232,244,239));canvas.drawRoundRect(M,y,W-M,y+68,14,14,paint);int top=y;y+=20;paint.setTextSize(9);paint.setColor(Color.DKGRAY);canvas.drawText(a,M+16,y,paint);canvas.drawText(b,W/2+10,y,paint);y=top+48;paint.setTextSize(15);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));paint.setColor(Color.rgb(30,106,87));canvas.drawText(av,M+16,y,paint);canvas.drawText(bv,W/2+10,y,paint);y=top+68;}}
}
