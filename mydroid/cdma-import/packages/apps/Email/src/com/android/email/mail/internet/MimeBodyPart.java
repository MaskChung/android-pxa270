
package com.android.email.mail.internet;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import com.android.email.mail.Body;
import com.android.email.mail.BodyPart;
import com.android.email.mail.MessagingException;

/**
 * TODO this is a close approximation of Message, need to update along with
 * Message.
 */
public class MimeBodyPart extends BodyPart {
    protected MimeHeader mHeader = new MimeHeader();
    protected Body mBody;
    protected int mSize;

    public MimeBodyPart() throws MessagingException {
        this(null);
    }

    public MimeBodyPart(Body body) throws MessagingException {
        this(body, null);
    }

    public MimeBodyPart(Body body, String mimeType) throws MessagingException {
        if (mimeType != null) {
            setHeader(MimeHeader.HEADER_CONTENT_TYPE, mimeType);
        }
        setBody(body);
    }

    protected String getFirstHeader(String name) throws MessagingException {
        return mHeader.getFirstHeader(name);
    }

    public void addHeader(String name, String value) throws MessagingException {
        mHeader.addHeader(name, value);
    }

    public void setHeader(String name, String value) throws MessagingException {
        mHeader.setHeader(name, value);
    }

    public String[] getHeader(String name) throws MessagingException {
        return mHeader.getHeader(name);
    }

    public void removeHeader(String name) throws MessagingException {
        mHeader.removeHeader(name);
    }

    public Body getBody() throws MessagingException {
        return mBody;
    }

    public void setBody(Body body) throws MessagingException {
        this.mBody = body;
        if (body instanceof com.android.email.mail.Multipart) {
            com.android.email.mail.Multipart multipart = ((com.android.email.mail.Multipart)body);
            multipart.setParent(this);
            setHeader(MimeHeader.HEADER_CONTENT_TYPE, multipart.getContentType());
        }
        else if (body instanceof TextBody) {
            String contentType = String.format("%s;\n charset=utf-8", getMimeType());
            String name = MimeUtility.getHeaderParameter(getContentType(), "name");
            if (name != null) {
                contentType += String.format(";\n name=\"%s\"", name);
            }
            setHeader(MimeHeader.HEADER_CONTENT_TYPE, contentType);
            setHeader(MimeHeader.HEADER_CONTENT_TRANSFER_ENCODING, "base64");
        }
    }

    public String getContentType() throws MessagingException {
        String contentType = getFirstHeader(MimeHeader.HEADER_CONTENT_TYPE);
        if (contentType == null) {
            return "text/plain";
        } else {
            return contentType;
        }
    }

    public String getDisposition() throws MessagingException {
        String contentDisposition = getFirstHeader(MimeHeader.HEADER_CONTENT_DISPOSITION);
        if (contentDisposition == null) {
            return null;
        } else {
            return contentDisposition;
        }
    }

    public String getMimeType() throws MessagingException {
        return MimeUtility.getHeaderParameter(getContentType(), null);
    }

    public boolean isMimeType(String mimeType) throws MessagingException {
        return getMimeType().equals(mimeType);
    }

    public int getSize() throws MessagingException {
        return mSize;
    }

    /**
     * Write the MimeMessage out in MIME format.
     */
    public void writeTo(OutputStream out) throws IOException, MessagingException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out), 1024);
        mHeader.writeTo(out);
        writer.write("\r\n");
        writer.flush();
        if (mBody != null) {
            mBody.writeTo(out);
        }
    }
}
