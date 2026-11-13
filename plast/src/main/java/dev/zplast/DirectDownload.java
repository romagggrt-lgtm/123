package dev.zplast;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.net.ssl.*;

/** Bounded public HTTP(S) fetch: pin the validated DNS address for each hop. */
final class DirectDownload {
 private static final int CONNECT_MS=10000, READ_MS=15000, MAX_HEADERS=32768;
 private static final long TOTAL_NANOS=90_000_000_000L;
 record Result(URI uri,long bytes){}
 static Result download(String link,Path output,long limit)throws IOException{
  URI uri;try{uri=URI.create(link);}catch(IllegalArgumentException e){throw new IOException("Некорректная ссылка.");}
  long deadline=System.nanoTime()+TOTAL_NANOS;
  for(int redirects=0;redirects<=3;redirects++){
   validate(uri);String host=uri.getHost();int port=uri.getPort()<0?(uri.getScheme().equalsIgnoreCase("https")?443:80):uri.getPort();
   InetAddress[] ips=InetAddress.getAllByName(host);if(ips.length==0)throw new IOException("Адрес не найден.");
   // Refuse any mixed public/private result rather than trusting DNS order.
   for(InetAddress ip:ips)if(!isPublic(ip))throw new IOException("Разрешены только публичные адреса в интернете.");
   if(System.nanoTime()>deadline)throw new IOException("Сервер не ответил вовремя.");
   Socket raw=new Socket();
   try{
    raw.connect(new InetSocketAddress(ips[0],port),CONNECT_MS);raw.setSoTimeout(READ_MS);Socket socket=raw;
    if(uri.getScheme().equalsIgnoreCase("https")){
     SSLSocket tls=(SSLSocket)((SSLSocketFactory)SSLSocketFactory.getDefault()).createSocket(raw,host,port,true);
     SSLParameters params=tls.getSSLParameters();params.setEndpointIdentificationAlgorithm("HTTPS");tls.setSSLParameters(params);tls.startHandshake();socket=tls;
    }
    Socket connected=socket;
    try(connected){
     String path=uri.getRawPath();if(path==null||path.isEmpty())path="/";if(uri.getRawQuery()!=null)path+="?"+uri.getRawQuery();
     String hostHeader=host.contains(":")&&!host.startsWith("[")?"["+host+"]":host;
     if(uri.getPort()>=0)hostHeader+=":"+port;
     String request="GET "+path+" HTTP/1.1\r\nHost: "+hostHeader+"\r\nUser-Agent: Z_Plast/1.1\r\nAccept: audio/mpeg,application/octet-stream,*/*\r\nAccept-Encoding: identity\r\nConnection: close\r\n\r\n";
     connected.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));connected.getOutputStream().flush();
     InputStream in=new BufferedInputStream(connected.getInputStream());int[] used={0};String status=line(in,used,deadline);
     if(status==null||!status.matches("HTTP/1\\.[01] [0-9]{3}(?: .*)?"))throw new IOException("Сервер вернул неправильный ответ.");int code=Integer.parseInt(status.substring(9,12));Map<String,String> headers=new HashMap<>();
     for(;;){String h=line(in,used,deadline);if(h==null)throw new IOException("Неполный ответ сервера.");if(h.isEmpty())break;int colon=h.indexOf(':');if(colon<=0)throw new IOException("Некорректный ответ сервера.");String key=h.substring(0,colon).trim().toLowerCase(Locale.ROOT),value=h.substring(colon+1).trim();if(headers.putIfAbsent(key,value)!=null&&(key.equals("content-length")||key.equals("transfer-encoding")))throw new IOException("Некорректный ответ сервера.");}
     if(code>=300&&code<400){String next=headers.get("location");if(next==null||redirects==3)throw new IOException("Слишком много перенаправлений.");try{uri=uri.resolve(next);}catch(IllegalArgumentException e){throw new IOException("Некорректное перенаправление.");}continue;}
     if(code!=200)throw new IOException("Сайт не отдал файл (HTTP "+code+").");
     String encoding=headers.getOrDefault("content-encoding","identity");if(!encoding.equalsIgnoreCase("identity"))throw new IOException("Сайт отдал неподдерживаемый формат ответа.");
     String transfer=headers.get("transfer-encoding"),length=headers.get("content-length");long expected=-1;
     if(length!=null)try{expected=Long.parseLong(length);if(expected<0||expected>limit)throw new IOException("Файл слишком большой.");}catch(NumberFormatException ex){throw new IOException("Некорректный размер файла.");}
     if(transfer!=null&&!transfer.equalsIgnoreCase("chunked"))throw new IOException("Неподдерживаемая передача файла.");if(transfer!=null&&length!=null)throw new IOException("Некорректный ответ сервера.");
     long size;try(OutputStream out=Files.newOutputStream(output,StandardOpenOption.TRUNCATE_EXISTING)){
      if(transfer!=null)size=chunked(in,out,limit,deadline);else size=copy(in,out,expected,limit,deadline);
     }
     if(size==0)throw new IOException("Пустой файл.");return new Result(uri,size);
    }
   }finally{try{raw.close();}catch(IOException ignored){}}
  }throw new IOException("Слишком много перенаправлений.");
 }
 static void validate(URI uri)throws IOException{
  if(uri==null||uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null||uri.toASCIIString().length()>2048||uri.getPort()>65535||uri.getPort()==0||!Set.of("http","https").contains(uri.getScheme()==null?"":uri.getScheme().toLowerCase(Locale.ROOT)))throw new IOException("Нужна прямая ссылка http:// или https:// на MP3.");
 }
 static boolean isPublic(InetAddress ip){
  if(ip.isAnyLocalAddress()||ip.isLoopbackAddress()||ip.isLinkLocalAddress()||ip.isSiteLocalAddress()||ip.isMulticastAddress())return false;byte[] a=ip.getAddress();
  if(a.length==16)return (a[0]&0xe0)==0x20 && !((a[0]&255)==0x20&&(a[1]&255)==0x01&&(a[2]&255)==0x0d&&(a[3]&255)==0xb8);
  int x=a[0]&255,y=a[1]&255,z=a[2]&255;
  return !(x==0||x==10||x==127||x>=224||(x==100&&y>=64&&y<=127)||(x==169&&y==254)||(x==172&&y>=16&&y<=31)||(x==192&&(y==168||(y==0&&(z==0||z==2))))||(x==198&&(y==18||y==19||(y==51&&z==100)))||(x==203&&y==0&&z==113));
 }
 private static String line(InputStream in,int[] total,long deadline)throws IOException{
  ByteArrayOutputStream b=new ByteArrayOutputStream();int previous=-1;
  for(;;){if(System.nanoTime()>deadline)throw new IOException("Время загрузки истекло.");int c=in.read();if(c<0)return b.size()==0?null:throwPartial();if(++total[0]>MAX_HEADERS||b.size()>8192)throw new IOException("Слишком большой заголовок ответа.");if(previous=='\r'&&c=='\n'){byte[] raw=b.toByteArray();return new String(raw,0,raw.length-1,StandardCharsets.ISO_8859_1);}if(c=='\n')throw new IOException("Некорректный заголовок ответа.");b.write(c);previous=c;}
 }
 private static String throwPartial()throws IOException{throw new IOException("Неполный заголовок ответа.");}
 private static long copy(InputStream in,OutputStream out,long expected,long limit,long deadline)throws IOException{
  byte[] buf=new byte[8192];long count=0;
  while(expected<0||count<expected){if(System.nanoTime()>deadline||Thread.currentThread().isInterrupted())throw new IOException("Время загрузки истекло.");int len=(int)Math.min(buf.length,expected<0?buf.length:expected-count);int n=in.read(buf,0,len);if(n<0){if(expected>=0)throw new IOException("Файл загружен не полностью.");break;}count+=n;if(count>limit)throw new IOException("Файл слишком большой.");out.write(buf,0,n);}
  return count;
 }
 private static long chunked(InputStream in,OutputStream out,long limit,long deadline)throws IOException{
  long total=0;for(;;){String h=line(in,new int[]{0},deadline);if(h==null)throw new IOException("Неполный ответ сервера.");String hex=h.split(";",2)[0].trim();long n;try{if(hex.length()>16)throw new NumberFormatException();n=Long.parseLong(hex,16);}catch(NumberFormatException e){throw new IOException("Некорректный размер блока.");}if(n<0||n>limit-total)throw new IOException("Файл слишком большой.");if(n==0){int[] headers={0};while(true){String trailer=line(in,headers,deadline);if(trailer==null||trailer.isEmpty())break;}return total;}total+=copy(in,out,n,limit-total,deadline);if(in.read()!='\r'||in.read()!='\n')throw new IOException("Повреждённая передача файла.");}
 }
}
