package com.nttdocomo.ui;

import com.nttdocomo.lang._XStringSupport;
import com.nttdocomo.opt.ui.PointingDevice;
import opendoja.host.DoJaRuntime;
import opendoja.host.HostTextInput;
import java.util.*;

/** Renders high-level widgets through the same canvas, fonts and input queue as DoJa games. */
public final class _PanelHost {
    private static final int GAP = 4, PAD = 4, FOCUS = 0xff245eb8;
    private final Panel panel;
    private final Canvas canvas;
    private final Map<Component, Placement> placements = new IdentityHashMap<>();
    private final Map<ListBox, Integer> listCursors = new IdentityHashMap<>();
    private Component pointerPressed;
    private int scroll, contentHeight, titleHeight;

    _PanelHost(Panel panel) {
        this.panel = panel;
        canvas = new Canvas() {
            @Override public void paint(Graphics graphics) { _PanelHost.this.paint(graphics); }
            @Override public void processEvent(int type, int key) { event(type, key); }
        };
    }

    /** Keeps Display.getCurrent() as the application Panel while sharing canvas presentation. */
    public static Canvas canvasFor(Frame frame) {
        if (frame instanceof Canvas canvas) return canvas;
        if (frame instanceof Panel panel) { panel.host.shown(); return panel.host.canvas; }
        return null;
    }

    void added(Component component, int breaks, String alignment) {
        placements.put(component, new Placement(breaks, alignment));
    }

    void changed() {
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime != null && runtime.getCurrentFrame() == panel) runtime.requestWidgetRepaint(canvas);
    }

    private void shown() {
        layout();
        if (panel.hostFocusedComponent() == null) {
            for (Component component : components()) if (focusable(component)) {
                panel.requestFocus(component); break;
            }
        }
    }

    void focusChanged(Component component) {
        int height = Math.max(1, panel.getHeight() - titleHeight);
        if (component.getY() < scroll) scroll = component.getY();
        else if (component.getY() + component.getHeight() > scroll + height)
            scroll = component.getY() + component.getHeight() - height;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - height)));
    }

    private List<Component> components() { return new ArrayList<>(panel.hostComponents()); }
    private boolean focusable(Component c) { return c instanceof Interactable && c.acceptsFocusOn(panel); }

    private void layout() {
        titleHeight = panel.hostTitle().isEmpty() ? 0 : Font.getDefaultFont().getHeight() + 8;
        int width = Math.max(1, panel.getWidth() - 2 * PAD - 4);
        int y = PAD, rowWidth = 0, rowHeight = 0;
        List<Component> row = new ArrayList<>(); String alignment = HTMLLayout.LEFT;
        contentHeight = 0;
        for (Component component : components()) {
            if (!component.visible()) continue;
            int[] size = preferredSize(component, width);
            if (panel.hostLayoutManager() == null) {
                component.hostBounds(component.getX(), component.getY(), size[0], size[1]);
                contentHeight = Math.max(contentHeight, component.getY() + size[1] + PAD);
                continue;
            }
            Placement placement = placements.getOrDefault(component, new Placement(0, HTMLLayout.LEFT));
            int breaks = placement.breaks();
            boolean wrap = !row.isEmpty() && rowWidth + GAP + size[0] > width;
            if (!row.isEmpty() && (breaks > 0 || wrap || !Objects.equals(alignment, placement.alignment()))) {
                placeRow(row, y, rowWidth, rowHeight, width, alignment);
                y += rowHeight + GAP + Math.max(0, breaks - 1) * (Font.getDefaultFont().getHeight() + GAP);
                row.clear(); rowWidth = 0; rowHeight = 0;
            }
            alignment = placement.alignment();
            component.hostBounds(0, 0, size[0], size[1]);
            row.add(component); rowWidth += (row.size() > 1 ? GAP : 0) + size[0];
            rowHeight = Math.max(rowHeight, size[1]);
        }
        if (panel.hostLayoutManager() != null) {
            placeRow(row, y, rowWidth, rowHeight, width, alignment);
            contentHeight = y + rowHeight + PAD;
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - (panel.getHeight() - titleHeight))));
    }

    private void placeRow(List<Component> row, int y, int rowWidth, int rowHeight, int width, String alignment) {
        int x = PAD + (HTMLLayout.CENTER.equals(alignment) ? Math.max(0, (width - rowWidth) / 2)
                : HTMLLayout.RIGHT.equals(alignment) ? Math.max(0, width - rowWidth) : 0);
        for (Component component : row) {
            component.hostBounds(x, y + (rowHeight - component.getHeight()) / 2, component.getWidth(), component.getHeight());
            x += component.getWidth() + GAP;
        }
    }

    private int[] preferredSize(Component c, int available) {
        if (c.hostExplicitSize()) return new int[]{Math.max(1,c.getWidth()), Math.max(1,c.getHeight())};
        Font font = c.font(); int h = font.getHeight(); int w = available;
        if (c instanceof TextBox text) {
            w = text.hostColumns() == 0 ? available : text.hostColumns() * Math.max(1,font.stringWidth("W")) + 8;
            h = Math.max(1,text.hostRows()) * h + 8;
        } else if (c instanceof Label label) { w = font.stringWidth(label.hostText()) + 4; h += 6; }
        else if (c instanceof Button button) { w = font.stringWidth(button.hostLabel()) + 16; h += 10; }
        else if (c instanceof ImageLabel label) {
            Image image = label.hostImage(); w = image == null ? 1 : image.getWidth(); h = image == null ? 1 : image.getHeight();
        } else if (c instanceof Ticker) h += 6;
        else if (c instanceof ListBox list) h = list.hostRows() * (h + 6) + 4;
        else if (c instanceof ImageButton button) {
            Image image = button.hostImage(); w = image == null ? 12 : image.getWidth()+8; h = image == null ? h+8 : image.getHeight()+8;
        } else if (c instanceof AnchorButton anchor) {
            Image image = anchor.hostImage(); w = font.stringWidth(anchor.hostText()) + (image == null ? 0 : image.getWidth()+GAP) + 8;
            h = Math.max(h, image == null ? 0 : image.getHeight()) + 8;
        }
        return new int[]{Math.max(1,Math.min(available,w)),Math.max(1,h)};
    }

    private void paint(Graphics g) {
        layout(); g.clearClip(); g.setOrigin(0,0); g.setColor(panel.backgroundColor());
        g.fillRect(0,0,panel.getWidth(),panel.getHeight());
        if (titleHeight > 0) {
            g.setColor(0xff254f83); g.fillRect(0,0,panel.getWidth(),titleHeight);
            g.setFont(Font.getDefaultFont()); g.setColor(0xffffffff);
            g.drawString(panel.hostTitle(),PAD,4+Font.getDefaultFont().getAscent());
        }
        for (Component c : components()) {
            if (!c.visible()) continue;
            int x=c.getX(), y=c.getY()+titleHeight-scroll, w=c.getWidth(), h=c.getHeight();
            int top=Math.max(titleHeight,y), bottom=Math.min(panel.getHeight(),y+h);
            if (bottom<=top) continue;
            g.setClip(Math.max(0,x),top,Math.max(0,Math.min(w,panel.getWidth()-x)),bottom-top);
            g.setFont(c.font()); g.setColor(c.backgroundColor()); g.fillRect(x,y,w,h);
            if (c instanceof Label label) text(g,label.hostText(),c,x+2,y+3,w-4,label.hostAlignment());
            else if (c instanceof TextBox box) {
                border(g,c,x,y,w,h,box.hostEnabled());
                text(g,box.hostDisplayText(),c,x+4,y+4,w-8,Label.LEFT);
            } else if (c instanceof Button button) {
                button(g,c,button.hostLabel(),x,y,w,h,button.hostEnabled());
            } else if (c instanceof ImageLabel label) {
                if(label.hostImage()!=null)g.drawImage(label.hostImage(),x,y);
            } else if (c instanceof Ticker ticker) text(g,ticker.hostText(),c,x+2,y+3,w-4,Label.LEFT);
            else if (c instanceof ImageButton button) {
                border(g,c,x,y,w,h,button.hostEnabled());
                if(button.hostImage()!=null)g.drawImage(button.hostImage(),x+4,y+4);
            } else if(c instanceof AnchorButton anchor) {
                border(g,c,x,y,w,h,anchor.hostEnabled());
                int imageWidth=anchor.hostImage()==null?0:anchor.hostImage().getWidth()+GAP;
                if(anchor.hostImage()!=null)g.drawImage(anchor.hostImage(),x+4,y+4);
                text(g,anchor.hostText(),c,x+4+imageWidth,y+4,w-8-imageWidth,Label.LEFT);
            } else if(c instanceof ListBox list) paintList(g,list,x,y,w,h);
        }
        g.clearClip();
        int visible = Math.max(1,panel.getHeight()-titleHeight);
        if (contentHeight>visible) {
            int thumb=Math.max(8,visible*visible/contentHeight);
            g.setColor(0xffb0b0b0);g.fillRect(panel.getWidth()-3,titleHeight,3,visible);
            g.setColor(FOCUS);g.fillRect(panel.getWidth()-3,titleHeight+scroll*(visible-thumb)/Math.max(1,contentHeight-visible),3,thumb);
        }
    }

    private void text(Graphics g,String text,Component c,int x,int y,int width,int alignment) {
        g.setColor(c.foregroundColor());
        String[] lines=text.split("\\r?\\n",-1);
        for(int i=0;i<lines.length;i++) {
            int offset=alignment==Label.CENTER?(width-c.font().stringWidth(lines[i]))/2:
                    alignment==Label.RIGHT?width-c.font().stringWidth(lines[i]):0;
            g.drawString(lines[i],x+Math.max(0,offset),y+i*c.font().getHeight()+c.font().getAscent());
        }
    }

    private void border(Graphics g,Component c,int x,int y,int w,int h,boolean enabled) {
        g.setColor(!enabled?0xffaaaaaa:c.focused()?FOCUS:0xff666666);g.drawRect(x,y,w-1,h-1);
        if(c.focused())g.drawRect(x+1,y+1,Math.max(0,w-3),Math.max(0,h-3));
    }
    private void button(Graphics g,Component c,String label,int x,int y,int w,int h,boolean enabled) {
        g.setColor(!enabled?0xffdddddd:c.focused()?0xffd5e7ff:0xffeeeeee);g.fillRect(x,y,w,h);
        border(g,c,x,y,w,h,enabled);
        g.setColor(enabled?c.foregroundColor():0xff888888);
        g.drawString(label,x+Math.max(2,(w-c.font().stringWidth(label))/2),y+(h-c.font().getHeight())/2+c.font().getAscent());
    }
    private void paintList(Graphics g,ListBox list,int x,int y,int w,int h) {
        border(g,list,x,y,w,h,list.hostEnabled());
        int cursor=listCursor(list);
        int first=Math.max(0,cursor-list.hostRows()+1), step=list.font().getHeight()+6;
        for(int row=0;row<list.hostRows()&&first+row<list.getItemCount();row++) {
            int index=first+row;
            if(index==cursor&&list.focused()) {g.setColor(0xffd5e7ff);g.fillRect(x+2,y+2+row*step,w-4,step);}
            String marker=list.hostType()==ListBox.CHECK_BOX||list.hostType()==ListBox.MULTIPLE_SELECT?
                    (list.isIndexSelected(index)?"[x] ":"[ ] "):"";
            String value=list.getItem(index);
            if(value==null)value=_XStringSupport.valueOrEmpty(list.getXItem(index));
            text(g,marker+value,list,x+4,y+4+row*step,w-8,Label.LEFT);
        }
    }

    private void event(int type,int key) {
        if(Display.getCurrent()!=panel)return;
        var runtime=DoJaRuntime.current();
        if(runtime!=null&&runtime.isDialogShowing())return;
        layout();
        if(type==67||type==68) { pointer(type,PointingDevice.getX(),PointingDevice.getY()); return; }
        if(type!=Display.KEY_PRESSED_EVENT&&type!=Display.KEY_RELEASED_EVENT)return;
        panel.hostKeyEvent(type,key);
        if(Display.getCurrent()!=panel)return;
        if(key==Display.KEY_SOFT1||key==Display.KEY_SOFT2) {
            panel.processSoftKeyEvent(type,key==Display.KEY_SOFT1?Frame.SOFT_KEY_1:Frame.SOFT_KEY_2);return;
        }
        if(type!=Display.KEY_PRESSED_EVENT)return;
        Component focused=panel.hostFocusedComponent();
        if(focused instanceof ListBox list && list.getItemCount()>0 && (key==Display.KEY_UP||key==Display.KEY_DOWN)) {
            int cursor=listCursor(list);
            int next=cursor+(key==Display.KEY_UP?-1:1);
            if(next>=0&&next<list.getItemCount()) {
                listCursors.put(list,next);
                if(list.hostType()!=ListBox.CHECK_BOX&&list.hostType()!=ListBox.MULTIPLE_SELECT)list.select(next);
                changed();return;
            }
        }
        switch(key) {
            case Display.KEY_UP, Display.KEY_LEFT -> moveFocus(-1);
            case Display.KEY_DOWN, Display.KEY_RIGHT -> moveFocus(1);
            case Display.KEY_SELECT -> activate(focused);
            case Display.KEY_PAGE_UP -> {scroll=Math.max(0,scroll-Math.max(1,panel.getHeight()-titleHeight));changed();}
            case Display.KEY_PAGE_DOWN -> {scroll=Math.min(Math.max(0,contentHeight-panel.getHeight()+titleHeight),scroll+Math.max(1,panel.getHeight()-titleHeight));changed();}
            default -> { }
        }
    }
    private void moveFocus(int direction) {
        List<Component> targets=components().stream().filter(this::focusable).toList();
        if(targets.isEmpty())return;
        int index=targets.indexOf(panel.hostFocusedComponent());
        index=index<0?(direction>0?0:targets.size()-1):Math.floorMod(index+direction,targets.size());
        panel.requestFocus(targets.get(index));
    }
    private void pointer(int type,int x,int y) {
        Component hit=null;
        for(Component c:components()) if(focusable(c)&&x>=c.getX()&&x<c.getX()+c.getWidth()
                &&y>=Math.max(titleHeight,c.getY()+titleHeight-scroll)&&y<c.getY()+titleHeight-scroll+c.getHeight())hit=c;
        if(type==67) {pointerPressed=hit;if(hit!=null)panel.requestFocus(hit);}
        else {Component pressed=pointerPressed;pointerPressed=null;if(hit!=null&&pressed==hit)activate(hit);}
    }
    private void activate(Component component) {
        if(component==null||!focusable(component))return;
        if(component instanceof TextBox box) {
            if(box.hostEditable())HostTextInput.requestTextBox(panel,box,box.getText(),box.hostDisplayMode(),box.hostInputMode(),box.hostInputSize(),box.hostRows()>1,
                    value -> { if(box.ownerPanel()==panel&&box.visible()&&box.hostEditable())box.setText(value); });
        } else if(component instanceof ListBox list && list.getItemCount()>0) {
            int cursor=listCursor(list);
            if((list.hostType()==ListBox.CHECK_BOX||list.hostType()==ListBox.MULTIPLE_SELECT)&&list.isIndexSelected(cursor))list.deselect(cursor);
            else list.select(cursor);
        } else panel.fireComponentAction(component,ComponentListener.BUTTON_PRESSED,0);
        changed();
    }
    private int listCursor(ListBox list) {
        return Math.max(0,Math.min(list.getItemCount()-1,listCursors.getOrDefault(list,Math.max(0,list.getSelectedIndex()))));
    }
    private record Placement(int breaks,String alignment) { }
}
