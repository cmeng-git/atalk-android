/*  1:   */ package org.atalk.android.util.javax.sound.sampled;
/*  2:   */ 
/*  3:   */ import java.util.Map;
/*  4:   */ 
/*  5:   */ public class AudioFormat
/*  6:   */ {
/*  7:   */   protected Encoding encoding;
/*  8:   */   
/*  9:   */   public AudioFormat(float sampleRate, int sampleSizeInBits, int channels, boolean signed, boolean bigEndian) {}
/* 10:   */   
/* 11:   */   public AudioFormat(Encoding encoding, float sampleRate, int sampleSizeInBits, int channels, int frameSize, float frameRate, boolean bigEndian) {}
/* 12:   */   
/* 13:   */   public AudioFormat(Encoding encoding, float sampleRate, int sampleSizeInBits, int channels, int frameSize, float frameRate, boolean bigEndian, Map<String, Object> properties) {}
/* 14:   */   
/* 15:   */   public float getSampleRate()
/* 16:   */   {
/* 17:26 */     return 0.0F;
/* 18:   */   }
/* 19:   */   
/* 20:   */   public int getSampleSizeInBits()
/* 21:   */   {
/* 22:27 */     return 0;
/* 23:   */   }
/* 24:   */   
/* 25:   */   public int getChannels()
/* 26:   */   {
/* 27:28 */     return 1;
/* 28:   */   }
/* 29:   */   
/* 30:   */   public boolean isBigEndian()
/* 31:   */   {
/* 32:30 */     return false;
/* 33:   */   }
/* 34:   */   
/* 35:   */   public float getFrameRate()
/* 36:   */   {
/* 37:31 */     return 0.0F;
/* 38:   */   }
/* 39:   */   
/* 40:   */   public int getFrameSize()
/* 41:   */   {
/* 42:32 */     return 0;
/* 43:   */   }
/* 44:   */   
/* 45:   */   public Encoding getEncoding()
/* 46:   */   {
/* 47:35 */     return this.encoding;
/* 48:   */   }
/* 49:   */   
/* 50:   */   public static class Encoding
/* 51:   */   {
/* 52:40 */     public static final Encoding PCM_SIGNED = new Encoding("PCM_SIGNED");
/* 53:42 */     public static final Encoding PCM_UNSIGNED = new Encoding("PCM_UNSIGNED");
/* 54:44 */     public static final Encoding ULAW = new Encoding("ULAW");
/* 55:46 */     public static final Encoding ALAW = new Encoding("ALAW");
/* 56:   */     private String name;
/* 57:   */     
/* 58:   */     public Encoding(String name)
/* 59:   */     {
/* 60:52 */       this.name = name;
/* 61:   */     }
/* 62:   */   }
/* 63:   */ }



/* Location:           D:\workspace\Android\soTalk\sotalk\libs\java-stubs.jar

 * Qualified Name:     AudioFormat

 * JD-Core Version:    0.7.0.1

 */