package at.favre.lib.hood.page.entries;

import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import at.favre.lib.hood.R;
import at.favre.lib.hood.page.PageEntry;
import at.favre.lib.hood.page.ViewTemplate;

import static at.favre.lib.hood.page.entries.ViewTypes.VIEWTYPE_HEADER;

/**
 * A simple, non-interactive header used to separate other entries
 */
public class HeaderEntry implements PageEntry<CharSequence> {

    private final CharSequence header;
    private final Template template;
    private final boolean hideInLog;

    /**
     * @param header as shown in ui
     */
    public HeaderEntry(CharSequence header) {
        this(header, false);
    }

    public HeaderEntry(CharSequence header, boolean hideInLog) {
        this.header = header;
        this.template = new Template();
        this.hideInLog = hideInLog;
    }

    @Override
    public CharSequence getValue() {
        return header;
    }

    @Override
    public ViewTemplate<CharSequence> getViewTemplate() {
        return template;
    }

    @Override
    public String toLogString() {
        if (!hideInLog) {
            return "#" + header.toString();
        }
        return null;
    }

    @Override
    public void refresh() {
        //no-op
    }

    private static class Template implements ViewTemplate<CharSequence> {
        @Override
        public int getViewType() {
            return VIEWTYPE_HEADER;
        }

        @Override
        public View constructView(ViewGroup viewGroup, LayoutInflater inflater) {
            return inflater.inflate(R.layout.hoodlib_template_header, viewGroup, false);
        }

        @Override
        public void setContent(CharSequence value, @NonNull View view) {
            ((TextView) view.findViewById(R.id.title)).setText(value);
        }

        @Override
        public void decorateViewWithZebra(@NonNull View view, @ColorInt int zebraColor, boolean isOdd) {
            //no-op
        }
    }
}