package com.docomostar.opt.ui.eruption;

import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferUnderflowException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Loads Eruption assets without a platform DLL; rendering remains in the shared GLES backend. */
public final class Loader {
    public Loader() { }
    public static Object3D[] load(InputStream input,int flags) throws IOException {
        byte[] bytes=input.readNBytes(EruptionData.MAX_DATA+1);
        if(bytes.length>EruptionData.MAX_DATA) throw new IOException("Eruption asset too large");
        return load(bytes,flags);
    }
    public static Object3D[] load(byte[] bytes,int flags) throws IOException {
        try {
            EruptionData in=new EruptionData(bytes);
            String magic=new String(in.bytes(4),StandardCharsets.US_ASCII);
            int format=in.i32();
            if(format!=0) throw new IOException("Unsupported Eruption coordinate format: "+format);
            return switch(magic) {
                case "MCT_" -> textures(in);
                case "MCM_" -> model(in);
                case "MCA_" -> actions(in);
                default -> throw new IOException("Unsupported Eruption asset: "+magic);
            };
        } catch(BufferUnderflowException|IndexOutOfBoundsException|IllegalArgumentException failure) {
            throw new IOException("Malformed Eruption asset",failure);
        }
    }
    private static Object3D[] textures(EruptionData in) throws IOException {
        List<Texture> textures=new ArrayList<>();
        for(int n=in.u16();n>0;n--) {
            EruptionData r=in.record(); r.i32();r.i32();r.i32();int userId=r.i32();
            int gid=r.i32(),size=r.i32(),width=r.u16(),height=r.u16();
            boolean compressed=r.u8()!=0;int levels=r.u8(),format=r.u8();r.u8();
            if(levels!=0) r.bytes(levels*4);
            EruptionData pixels=r.payload(compressed,size);
            Texture texture=new Texture(gid,format,width,height,levels>0);texture.setUserId(userId);
            int paletteSize=format>=6&&format<=10?256:format>=11&&format<=15?16:0;
            int plainFormat=paletteSize==0?format:(format-6)%5+1;
            int[] palette=new int[paletteSize];
            for(int i=0;i<paletteSize;i++) palette[i]=pixel(pixels,plainFormat);
            if(paletteSize==0) for(int i=0;i<texture.pixels.length;i++) texture.pixels[i]=pixel(pixels,plainFormat);
            else if(paletteSize==256) for(int i=0;i<texture.pixels.length;i++) texture.pixels[i]=palette[pixels.u8()];
            else for(int i=0;i<texture.pixels.length;i+=2) {
                int pair=pixels.u8();texture.pixels[i]=palette[pair&15];
                if(i+1<texture.pixels.length) texture.pixels[i+1]=palette[pair>>>4];
            }
            textures.add(texture);
        }
        return new Object3D[]{new TextureTable(textures)};
    }
    private static int pixel(EruptionData in,int format) throws IOException {
        if(format==1||format==2) {
            int r=in.u8(),g=in.u8(),b=in.u8(),a=format==2?in.u8():255;
            return a<<24|r<<16|g<<8|b;
        }
        int value=in.u16();
        return switch(format) {
            case 3 -> 0xff000000|expand(value>>>11,31)<<16|expand(value>>>5&63,63)<<8|expand(value&31,31);
            case 4 -> (value&1)*255<<24|expand(value>>>11,31)<<16|expand(value>>>6&31,31)<<8|expand(value>>>1&31,31);
            case 5 -> (value&15)*17<<24|(value>>>12)*17<<16|(value>>>8&15)*17<<8|(value>>>4&15)*17;
            default -> throw new IOException("Unsupported Eruption texture format: "+format);
        };
    }
    private static int expand(int value,int max) { return value*255/max; }

    private record Record(int version,int id,int userId,EruptionData data) { }
    private static Object3D[] model(EruptionData in) throws IOException {
        in.i32();
        List<List<Record>> groups=new ArrayList<>();
        for(int group=0;group<12;group++) {
            List<Record> records=new ArrayList<>();
            for(int n=in.u16();n>0;n--) {
                EruptionData r=in.record();r.i32();int version=r.i32(),id=r.i32(),userId=r.i32();
                records.add(new Record(version,id,userId,r));
            }
            groups.add(records);
        }
        Map<Integer,Appearance> appearances=new HashMap<>();
        for(Record record:groups.get(0)) {
            EruptionData r=record.data;Appearance a=new Appearance();a.setUserId(record.userId);
            // Version one stores two texture stages following the render properties.
            if(record.version!=1) throw new IOException("Unsupported Eruption appearance version: "+record.version);
            for(int material=0;material<4;material++) a.colors[material]=r.i32();
            r.i32(); // Additional material colour.
            r.u8(); // Shininess; this app disables lighting.
            a.alphaThreshold=r.u8();
            a.setBlendMode(r.u8(),r.u8());
            r.u8(); // Shading mode.
            a.setCullingMode(r.u8());
            r.u8();r.u16();a.properties=r.i32();r.bytes(12);
            for(int unit=0;unit<2;unit++) {
                a.textureIds[unit]=r.i32();r.i32();a.textureBlend[unit]=r.u8();r.bytes(2);
                a.filters[unit][0]=r.u8();a.filters[unit][1]=r.u8();r.bytes(5+60);
            }
            appearances.put(record.id,a);
        }
        Map<Integer,VertexBuffer> vertices=new HashMap<>();
        for(Record record:groups.get(1)) vertices.put(record.id,vertices(record));
        Map<Integer,IndexBuffer> indices=new HashMap<>();
        for(Record record:groups.get(2)) {
            EruptionData r=record.data;boolean compressed=r.u8()!=0;int size=r.i32(),type=r.u8();r.u8();
            int first=r.u16(),stripCount=r.u16(),count=r.u16(),appearance=r.i32();r.i32();
            EruptionData data=r.payload(compressed,size);
            int[] lengths=new int[stripCount];for(int i=0;i<stripCount;i++) lengths[i]=data.u16();
            IndexBuffer index=new IndexBuffer(count);index.setUserId(record.userId);
            if(count==0) index.set(type,first,lengths);
            else {
                short[] values=new short[count];for(int i=0;i<count;i++) values[i]=(short)data.u16();
                index.set(type,lengths,values);
            }
            index.setAppearance(appearances.get(appearance));index.fix();indices.put(record.id,index);
        }
        Map<Integer,EruptionModel.Tree> trees=new HashMap<>();
        for(Record record:groups.get(7)) trees.put(record.id,tree(record.data));
        Map<Integer,EruptionModel.Skin> skins=new HashMap<>();
        for(Record record:groups.get(8)) skins.put(record.id,skin(record.data));
        Map<Integer,List<int[]>> nodes=new HashMap<>();
        for(Record record:groups.get(10)) {
            EruptionData r=record.data;int count=r.u16();r.i32();List<int[]> bindings=new ArrayList<>();
            for(int i=0;i<count;i++) bindings.add(new int[]{r.i32(),r.u16(),r.u8()});
            nodes.put(record.id,bindings);
        }
        Map<Integer,Figure> figures=new HashMap<>();
        Map<Figure,Integer> nodeRefs=new HashMap<>();
        for(Record record:groups.get(11)) {
            EruptionData r=record.data;Figure f=new Figure();f.setUserId(record.userId);
            for(int n=r.u16();n>0;n--) f.indices.add(required(indices,r.i32(),"index buffer"));
            int vertex=r.i32(),tree=r.i32(),node=r.i32(),skin=r.i32();r.i32();r.i32();r.i32();
            f.vertices=vertex==-1?null:required(vertices,vertex,"vertex buffer");
            f.tree=trees.get(tree);f.skin=skins.get(skin);
            nodeRefs.put(f,node);figures.put(record.id,f);
        }
        Set<Figure> children=new HashSet<>();
        for(var entry:nodeRefs.entrySet()) {
            List<int[]> bindings=nodes.get(entry.getValue());
            if(bindings==null) continue;
            for(int[] binding:bindings) {
                Figure part=figures.get(binding[0]);
                if(part!=null&&part!=entry.getKey()) {
                    if(part.skin!=null) part.skin.attachmentNode=binding[1];
                    entry.getKey().parts.add(new Figure.Part(part,binding[1]));children.add(part);
                }
            }
        }
        return figures.values().stream().filter(f->!children.contains(f)).toArray(Object3D[]::new);
    }
    private static <T> T required(Map<Integer,T> values,int id,String name) throws IOException {
        T value=values.get(id);if(value==null) throw new IOException("Missing Eruption "+name+": "+id);return value;
    }
    private static VertexBuffer vertices(Record record) throws IOException {
        EruptionData r=record.data;boolean compressed=r.u8()!=0;int size=r.i32();
        int[][] streams=new int[8][3];
        for(int i=0;i<5;i++) streams[i]=new int[]{r.u16(),r.u8(),r.u8()};
        int units=record.version==0?2:r.u8();
        if(units>2) throw new IOException("Unsupported Eruption UV stream count");
        for(int i=0;i<units;i++) streams[5+i]=new int[]{r.u16(),r.u8(),r.u8()};
        streams[7]=new int[]{r.u16(),r.u8(),r.u8()};
        float[][] scales=new float[units][4];
        for(int unit=0;unit<units;unit++) for(int i=0;i<4;i++) scales[unit][i]=r.f32();
        int count=streams[0][0],flags=1;
        for(int i=1;i<7;i++) if(streams[i][0]>0) flags|=1<<i;
        int max=count;for(int[] stream:streams) max=Math.max(max,stream[0]);
        VertexBuffer v=new VertexBuffer(flags,max);v.setUserId(record.userId);
        for(int stream=0;stream<streams.length;stream++) v.streamCounts[stream]=streams[stream][0];
        EruptionData data=r.payload(compressed,size);
        for(int stream=0;stream<8;stream++) {
            int[] desc=streams[stream];
            for(int vertex=0;vertex<desc[0];vertex++) for(int component=0;component<desc[2];component++) {
                float value=switch(desc[1]) {
                    case 0 -> data.f32();case 1 -> data.i32()/65536f;case 2 -> data.i32();case 3 -> (short)data.u16();
                    default -> throw new IOException("Unsupported Eruption vertex format: "+desc[1]);
                };
                if(stream==0&&component<3) v.positions[vertex*3+component]=value;
                if(stream==1&&component<3) v.normals[vertex*3+component]=desc[1]==3?value/32767:value;
                if(stream==4) {
                    int color=(int)value;
                    for(int c=0;c<4;c++) v.colors[vertex*4+c]=(byte)(color>>>(c*8));
                }
                if((stream==5||stream==6)&&component<2) v.texCoords[stream-5][vertex*2+component]=value;
            }
        }
        return v;
    }
    private static EruptionModel.Tree tree(EruptionData r) {
        r.i32();int count=r.u16();EruptionModel.Tree tree=new EruptionModel.Tree(count);
        for(int i=0;i<count;i++) {
            tree.parent[i]=(short)r.u16();float[] m=new float[16];m[15]=1;
            for(int c=0;c<4;c++) for(int row=0;row<3;row++) m[c*4+row]=r.f32();
            float x=r.f32(),y=r.f32(),z=r.f32();
            tree.local[i]=Transform.fromColumnMajor(m);tree.local[i].matrix.scale(x,y,z);r.bytes(29);
        }
        return tree;
    }
    private static EruptionModel.Skin skin(EruptionData r) throws IOException {
        boolean compressed=r.u8()!=0;int size=r.i32(),count=r.u16(),bones=r.u16();
        EruptionData data=r.payload(compressed,size);
        EruptionModel.Skin skin=new EruptionModel.Skin();skin.nodes=new int[bones];
        for(int i=0;i<bones;i++) skin.nodes[i]=data.u16();
        skin.bones=new int[count][];skin.weights=new float[count][];skin.copies=new int[count];
        for(int vertex=0;vertex<count;vertex++) {
            int weights=data.u8();skin.bones[vertex]=new int[weights];skin.weights[vertex]=new float[weights];
            skin.copies[vertex]=data.u8();
            for(int i=0;i<weights;i++) skin.weights[vertex][i]=data.f32();
            for(int i=0;i<weights;i++) skin.bones[vertex][i]=data.u8();
            float sum=0;for(float weight:skin.weights[vertex]) sum+=weight;
            if(sum>0) for(int i=0;i<weights;i++) skin.weights[vertex][i]/=sum;
        }
        return skin;
    }
    private static Object3D[] actions(EruptionData in) throws IOException {
        in.i32();int count=in.u16();ActionTable table=new ActionTable();
        for(int i=0;i<count;i++) {
            EruptionData r=in.record();r.i32();r.i32();int id=r.i32();r.i32();
            int target=r.i32();boolean compressed=r.u8()!=0;int size=r.i32();float max=r.f32();r.u8();r.u8();int nodes=r.u16();
            table.includeMaxFrame(max);
            EruptionData data=r.payload(compressed,size);
            for(int node=0;node<nodes;node++) for(int channels=data.u16();channels>0;channels--) {
                int component=data.u8(),type=data.u8(),keys=data.u16();data.u16();data.u16();
                int dimension=switch(type) { case 1,2,3,4 -> type;case 5 -> 12;
                    default -> throw new IOException("Unsupported Eruption animation value type: "+type); };
                ActionTable.Key[] frames=new ActionTable.Key[keys];
                for(int key=0;key<keys;key++) {
                    int interpolation=data.u8(),vectors=data.u8();float frame=data.f32();
                    if(vectors<1||vectors>3||interpolation<1||interpolation>4) throw new IOException("Invalid Eruption animation key");
                    float[][] values=new float[vectors][dimension];
                    for(int v=0;v<vectors;v++) for(int c=0;c<dimension;c++) values[v][c]=data.f32();
                    frames[key]=new ActionTable.Key(frame,interpolation,values);
                }
                table.tracks.add(new ActionTable.Track(target,node,component,frames));
            }
            if(data.remaining()!=0) throw new IOException("Unexpected Eruption animation data");
        }
        return new Object3D[]{table};
    }
}
