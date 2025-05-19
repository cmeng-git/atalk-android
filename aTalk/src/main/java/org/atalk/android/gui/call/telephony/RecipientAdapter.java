package org.atalk.android.gui.call.telephony;

import android.content.Context;
import android.text.Spannable;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import org.atalk.android.R;
import org.atalk.android.gui.call.telephony.RecipientSelectView.Recipient;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RecipientAdapter extends BaseAdapter implements Filterable
{
    private final Context context;
    private List<Recipient> recipients;
    private String highlight;
    private boolean showAdvancedInfo;


    public RecipientAdapter(Context context)
    {
        super();
        this.context = context;
    }

    public void setRecipients(List<Recipient> recipients)
    {
        this.recipients = recipients;
        notifyDataSetChanged();
    }

    public void setHighlight(String highlight)
    {
        this.highlight = highlight;
    }

    @Override
    public int getCount()
    {
        return recipients == null ? 0 : recipients.size();
    }

    @Override
    public Recipient getItem(int position)
    {
        return recipients == null ? null : recipients.get(position);
    }

    @Override
    public long getItemId(int position)
    {
        return position;
    }

    @Override
    public View getView(int position, View view, ViewGroup parent)
    {
        if (view == null) {
            view = newView(parent);
        }

        Recipient recipient = getItem(position);
        bindView(view, recipient);

        return view;
    }

    private View newView(ViewGroup parent)
    {
        View view = LayoutInflater.from(context).inflate(R.layout.recipient_dropdown_item, parent, false);

        RecipientTokenHolder holder = new RecipientTokenHolder(view);
        view.setTag(holder);
        return view;
    }

    private void bindView(View view, Recipient recipient)
    {
        RecipientTokenHolder holder = (RecipientTokenHolder) view.getTag();
        holder.name.setText(highlightText(recipient.getDisplayNameOrUnknown(context)));

        String address = recipient.address.getAddress();
        holder.phone.setText(highlightText(address));

        setContactPhotoOrPlaceholder(context, holder.photo, recipient);
    }

    public static void setContactPhotoOrPlaceholder(Context context, ImageView imageView, Recipient recipient)
    {
//        ContactPicture.getContactPictureLoader(context).loadContactPicture(address, imageView);
    }

    @Override
    public Filter getFilter()
    {
        return new Filter()
        {
            @Override
            protected FilterResults performFiltering(CharSequence constraint)
            {
                if (recipients == null) {
                    return null;
                }

                FilterResults result = new FilterResults();
                result.values = recipients;
                result.count = recipients.size();

                return result;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results)
            {
                notifyDataSetChanged();
            }
        };
    }

    public void setShowAdvancedInfo(boolean showAdvancedInfo)
    {
        this.showAdvancedInfo = showAdvancedInfo;
    }

    private static class RecipientTokenHolder
    {
        public final TextView name;
        public final TextView phone;
        final ImageView photo;

        RecipientTokenHolder(View view)
        {
            name = view.findViewById(R.id.text1);
            phone = view.findViewById(R.id.text2);
            photo = view.findViewById(R.id.contact_photo);
        }
    }

    private Spannable highlightText(String text)
    {
        Spannable highlightedSpannable = Spannable.Factory.getInstance().newSpannable(text);

        if (highlight == null) {
            return highlightedSpannable;
        }

        Pattern pattern = Pattern.compile(highlight, Pattern.LITERAL | Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            highlightedSpannable.setSpan(
                    new ForegroundColorSpan(context.getResources().getColor(android.R.color.holo_blue_light, null)),
                    matcher.start(), matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return highlightedSpannable;
    }
}
