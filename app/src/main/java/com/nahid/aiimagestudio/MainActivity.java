package com.nahid.aiimagestudio;

import android.app.*;
import android.content.*;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;

public class MainActivity extends Activity {
    EditText prompt, negative, width, height, steps, cfg, seed, batch;
    Spinner sizePreset;
    TextView modelText, status, downloadText;
    ProgressBar progress;
    ImageView result;
    File modelFile, engineFile;
    final Handler handler = new Handler(Looper.getMainLooper());
    static final int PICK_MODEL = 1001;
    static final String MODEL_NAME = "dreamshaper-7-lcm-q4_0.gguf";
    static final String MODEL_URL = "https://huggingface.co/darkmaniac7/TokForge-DreamShaper-LCM-GGUF-q4/resolve/main/dreamshaper-7-lcm-q4_0.gguf?download=true";
    static final String MODEL_MD5 = "03661e588d8728a4ec6141f64bd69842";
    final Random random = new Random();

    @Override public void onCreate(Bundle b) {
        super.onCreate(b); setContentView(R.layout.activity_main);
        prompt=findViewById(R.id.prompt); negative=findViewById(R.id.negative);
        width=findViewById(R.id.width); height=findViewById(R.id.height); steps=findViewById(R.id.steps);
        cfg=findViewById(R.id.cfg); seed=findViewById(R.id.seed); batch=findViewById(R.id.batch);
        sizePreset=findViewById(R.id.sizePreset); modelText=findViewById(R.id.modelText);
        status=findViewById(R.id.status); downloadText=findViewById(R.id.downloadText);
        progress=findViewById(R.id.progress); result=findViewById(R.id.result);
        setupSizePresets(); prepareEngine(); restoreModel();
        findViewById(R.id.downloadModel).setOnClickListener(v -> downloadRecommendedModel());
        findViewById(R.id.modelButton).setOnClickListener(v -> chooseModel());
        findViewById(R.id.randomSeed).setOnClickListener(v -> seed.setText(String.valueOf(nextSeed())));
        findViewById(R.id.generate).setOnClickListener(v -> generate());
        findViewById(R.id.gallery).setOnClickListener(v -> openGallery());
    }

    void setupSizePresets(){
        String[] p={"512×512 — Square (recommended)","768×432 — YouTube 16:9","1024×576 — YouTube 16:9 (slow)","432×768 — Shorts 9:16","576×1024 — Shorts 9:16 (slow)","512×640 — 4:5","Custom"};
        ArrayAdapter<String>a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,p);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); sizePreset.setAdapter(a);
        sizePreset.setSelection(0); sizePreset.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?>x,View v,int pos,long id){int w=512,h=512;switch(pos){case 1:w=768;h=432;break;case 2:w=1024;h=576;break;case 3:w=432;h=768;break;case 4:w=576;h=1024;break;case 5:w=512;h=640;break;default:return;}width.setText(""+w);height.setText(""+h);}
            public void onNothingSelected(AdapterView<?>x){}
        });
    }

    void prepareEngine(){new Thread(()->{try{
        File dir=new File(getFilesDir(),"engine");dir.mkdirs();engineFile=new File(dir,"sd-cli");
        if(!engineFile.exists()){try(InputStream in=getAssets().open("engine/arm64-v8a/sd-cli");OutputStream o=new FileOutputStream(engineFile)){byte[]b=new byte[1024*1024];int n;while((n=in.read(b))!=-1)o.write(b,0,n);}}
        engineFile.setExecutable(true,false); handler.post(()->status.setText("Engine ready • offline generation available"));
    }catch(Exception e){handler.post(()->status.setText("Engine missing. Build the release APK from GitHub Actions."));}}).start();}

    File modelsDir(){File d=new File(getExternalFilesDir(null),"models");d.mkdirs();return d;}
    void restoreModel(){String s=getPreferences(MODE_PRIVATE).getString("model",null);if(s!=null){modelFile=new File(s);if(modelFile.exists())modelText.setText("Model: "+modelFile.getName());}}

    void downloadRecommendedModel(){
        File f=new File(modelsDir(),MODEL_NAME);
        if(f.exists() && f.length()>1_500_000_000L){modelFile=f;saveModel(f);status.setText("Recommended model already downloaded");return;}
        progress.setIndeterminate(false);progress.setMax(100);progress.setProgress(0);progress.setVisibility(View.VISIBLE);
        findViewById(R.id.downloadModel).setEnabled(false); status.setText("Downloading model… keep Wi‑Fi connected");
        new Thread(()->{try{
            URL url=new URL(MODEL_URL); HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(30000);c.setReadTimeout(60000);c.setInstanceFollowRedirects(true);c.connect();
            int code=c.getResponseCode(); if(code<200||code>=300)throw new IOException("HTTP "+code);
            long total=c.getContentLengthLong(); File tmp=new File(modelsDir(),MODEL_NAME+".part");
            try(InputStream in=new BufferedInputStream(c.getInputStream());OutputStream o=new BufferedOutputStream(new FileOutputStream(tmp))){byte[]b=new byte[1024*1024];long done=0;int n;while((n=in.read(b))!=-1){o.write(b,0,n);done+=n;if(total>0){int pct=(int)Math.min(100,(done*100)/total);handler.post(()->{progress.setProgress(pct);downloadText.setText("Downloading: "+pct+"%" );});}}}
            c.disconnect();
            if(tmp.length()!=1625041920L) throw new IOException("Downloaded size is unexpected: "+tmp.length());
            String md5=md5(tmp);if(!MODEL_MD5.equalsIgnoreCase(md5))throw new IOException("MD5 mismatch");
            if(!f.exists() && !tmp.renameTo(f))throw new IOException("Could not finalize model");
            modelFile=f;saveModel(f);handler.post(()->{progress.setVisibility(View.GONE);findViewById(R.id.downloadModel).setEnabled(true);downloadText.setText("Recommended model ready ✓");modelText.setText("Model: "+f.getName());status.setText("Model ready • fully offline after this");});
        }catch(Exception e){handler.post(()->{progress.setVisibility(View.GONE);findViewById(R.id.downloadModel).setEnabled(true);status.setText("Download failed: "+e.getMessage());});}}).start();
    }
    String md5(File f)throws Exception{MessageDigest md=MessageDigest.getInstance("MD5");try(InputStream in=new BufferedInputStream(new FileInputStream(f))){byte[]b=new byte[1024*1024];int n;while((n=in.read(b))!=-1)md.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte x:md.digest())s.append(String.format(Locale.US,"%02x",x));return s.toString();}
    void saveModel(File f){getPreferences(MODE_PRIVATE).edit().putString("model",f.getAbsolutePath()).apply();}

    void chooseModel(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK_MODEL);}
    @Override protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);if(r!=PICK_MODEL||c!=RESULT_OK||d==null||d.getData()==null)return;Uri u=d.getData();status.setText("Copying model…");progress.setVisibility(View.VISIBLE);new Thread(()->{try{
        String name=queryName(u);if(name==null)name="model.gguf";File f=new File(modelsDir(),name);try(InputStream in=getContentResolver().openInputStream(u);OutputStream o=new FileOutputStream(f)){byte[]b=new byte[1024*1024];int n;while((n=in.read(b))!=-1)o.write(b,0,n);}modelFile=f;saveModel(f);
        handler.post(()->{modelText.setText("Model: "+f.getName());status.setText("Model ready");progress.setVisibility(View.GONE);});
    }catch(Exception e){handler.post(()->{progress.setVisibility(View.GONE);status.setText("Model copy failed: "+e.getMessage());});}}).start();}
    String queryName(Uri u){try(android.database.Cursor c=getContentResolver().query(u,new String[]{"_display_name"},null,null,null)){if(c!=null&&c.moveToFirst())return c.getString(0);}catch(Exception ignored){}return null;}

    void generate(){
        if(engineFile==null||!engineFile.exists()){Toast.makeText(this,"Engine is missing. Build the release APK first.",Toast.LENGTH_LONG).show();return;}
        if(modelFile==null||!modelFile.exists()){Toast.makeText(this,"Download the recommended model first.",Toast.LENGTH_LONG).show();return;}
        String p=prompt.getText().toString().trim();if(p.isEmpty()){Toast.makeText(this,"Enter a prompt",Toast.LENGTH_SHORT).show();return;}
        int w=parseInt(width,512),h=parseInt(height,512),st=parseInt(steps,6),bc=Math.max(1,Math.min(4,parseInt(batch,1)));float c=parseFloat(cfg,1.5f);long area=(long)w*h;
        if(w<256||h<256||w>1024||h>1024||w%8!=0||h%8!=0){Toast.makeText(this,"Use 256–1024 px, multiples of 8.",Toast.LENGTH_LONG).show();return;}
        if(area>786432L)Toast.makeText(this,"Large image: generation may be slow on Snapdragon 685.",Toast.LENGTH_LONG).show();
        progress.setIndeterminate(true);progress.setVisibility(View.VISIBLE);findViewById(R.id.generate).setEnabled(false);status.setText("Generating locally… do not close the app");
        final String neg=negative.getText().toString().trim();final long fixed=parseLong(seed,-1);File dir=new File(getExternalFilesDir(null),"outputs");dir.mkdirs();
        new Thread(()->{try{for(int i=0;i<bc;i++){long sd=fixed<0?nextSeed():fixed+i;File out=new File(dir,"nahid_"+System.currentTimeMillis()+"_"+sd+".png");
            ArrayList<String>cmd=new ArrayList<>();Collections.addAll(cmd,engineFile.getAbsolutePath(),"-m",modelFile.getAbsolutePath(),"-p",p,"-W",""+w,"-H",""+h,"--steps",""+st,"--cfg-scale",""+c,"-s",""+sd,"-o",out.getAbsolutePath(),"--sampling-method","lcm","--scheduler","lcm");if(!neg.isEmpty()){cmd.add("-n");cmd.add(neg);}
            Process proc=new ProcessBuilder(cmd).redirectErrorStream(true).start();BufferedReader br=new BufferedReader(new InputStreamReader(proc.getInputStream()));String line;while((line=br.readLine())!=null){final String z=line;handler.post(()->status.setText(z));}int code=proc.waitFor();if(code!=0||!out.exists())throw new IOException("Engine exit code "+code);saveToGallery(out);final File img=out;handler.post(()->result.setImageBitmap(BitmapFactory.decodeFile(img.getAbsolutePath())));}
            handler.post(()->{status.setText("Done • saved in Pictures/Nahid AI");progress.setVisibility(View.GONE);findViewById(R.id.generate).setEnabled(true);});
        }catch(Exception e){handler.post(()->{status.setText("Generation failed: "+e.getMessage());progress.setVisibility(View.GONE);findViewById(R.id.generate).setEnabled(true);});}}).start();
    }
    long nextSeed(){return Math.abs(random.nextLong()%2147483647L)+1;}
    void saveToGallery(File f)throws IOException{ContentValues v=new ContentValues();v.put(MediaStore.Images.Media.DISPLAY_NAME,f.getName());v.put(MediaStore.Images.Media.MIME_TYPE,"image/png");if(Build.VERSION.SDK_INT>=29){v.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Nahid AI");v.put(MediaStore.Images.Media.IS_PENDING,1);}Uri u=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);if(u==null)throw new IOException("Gallery insert failed");try(InputStream in=new FileInputStream(f);OutputStream o=getContentResolver().openOutputStream(u)){byte[]b=new byte[1024*1024];int n;while((n=in.read(b))!=-1)o.write(b,0,n);}if(Build.VERSION.SDK_INT>=29){ContentValues done=new ContentValues();done.put(MediaStore.Images.Media.IS_PENDING,0);getContentResolver().update(u,done,null,null);}}
    void openGallery(){Intent i=new Intent(Intent.ACTION_VIEW);i.setType("image/*");i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);try{startActivity(i);}catch(Exception e){Toast.makeText(this,"Open Gallery manually",Toast.LENGTH_SHORT).show();}}
    int parseInt(EditText e,int d){try{return Integer.parseInt(e.getText().toString());}catch(Exception x){return d;}}
    float parseFloat(EditText e,float d){try{return Float.parseFloat(e.getText().toString());}catch(Exception x){return d;}}
    long parseLong(EditText e,long d){try{return Long.parseLong(e.getText().toString());}catch(Exception x){return d;}}
}
