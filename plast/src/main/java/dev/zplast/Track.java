package dev.zplast;

/** Only server-generated IDs are ever used in filesystem or resource paths. */
public final class Track {
 public String id,name,owner,ownerName,sha256;
 public long created,size;
 public double seconds;
 public Track(){}
 public Track(String id,String name,String owner,String ownerName,String sha256,long created,long size,double seconds){
  this.id=id;this.name=name;this.owner=owner;this.ownerName=ownerName;this.sha256=sha256;this.created=created;this.size=size;this.seconds=seconds;
 }
}
