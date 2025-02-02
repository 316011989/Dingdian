package com.kk.taurus.playerbase.player;

import android.net.Uri;
import android.security.keystore.KeyProperties;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.upstream.BaseDataSource;
import com.google.android.exoplayer2.upstream.DataSourceException;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Predicate;
import com.google.android.exoplayer2.util.Util;
import com.kk.taurus.playerbase.config.PlayerLibrary;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.SecretKeySpec;

import androidx.annotation.Nullable;
import okhttp3.CacheControl;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static com.google.android.exoplayer2.util.Util.castNonNull;

/**
 * An {@link HttpDataSource} that delegates to Square's {@link Call.Factory}.
 */
public class CustomOkHttpDataSource extends BaseDataSource implements HttpDataSource {

    static {
        ExoPlayerLibraryInfo.registerModule("goog.exo.okhttp");
    }

    private static final byte[] SKIP_BUFFER = new byte[4096];

    private final Call.Factory callFactory;
    private final RequestProperties requestProperties;

    private final @Nullable
    String userAgent;
    private final @Nullable
    Predicate<String> contentTypePredicate;
    private final @Nullable
    CacheControl cacheControl;
    private final @Nullable
    RequestProperties defaultRequestProperties;

    private @Nullable
    DataSpec dataSpec;
    private @Nullable
    Response response;
    private @Nullable
    InputStream responseByteStream;
    private boolean opened;

    private long bytesToSkip;
    private long bytesToRead;

    private long bytesSkipped;
    private long bytesRead;

    /**
     * @param callFactory          A {@link Call.Factory} (typically an {@link okhttp3.OkHttpClient}) for use
     *                             by the source.
     * @param userAgent            An optional User-Agent string.
     * @param contentTypePredicate An optional {@link Predicate}. If a content type is rejected by the
     *                             predicate then a {@link InvalidContentTypeException} is thrown from {@link
     *                             #open(DataSpec)}.
     */
    public CustomOkHttpDataSource(
            Call.Factory callFactory,
            @Nullable String userAgent,
            @Nullable Predicate<String> contentTypePredicate) {
        this(
                callFactory,
                userAgent,
                contentTypePredicate,
                /* cacheControl= */ null,
                /* defaultRequestProperties= */ null);
    }

    /**
     * @param callFactory              A {@link Call.Factory} (typically an {@link okhttp3.OkHttpClient}) for use
     *                                 by the source.
     * @param userAgent                An optional User-Agent string.
     * @param contentTypePredicate     An optional {@link Predicate}. If a content type is rejected by the
     *                                 predicate then a {@link InvalidContentTypeException} is thrown from {@link
     *                                 #open(DataSpec)}.
     * @param cacheControl             An optional {@link CacheControl} for setting the Cache-Control header.
     * @param defaultRequestProperties The optional default {@link RequestProperties} to be sent to
     *                                 the server as HTTP headers on every request.
     */
    public CustomOkHttpDataSource(
            Call.Factory callFactory,
            @Nullable String userAgent,
            @Nullable Predicate<String> contentTypePredicate,
            @Nullable CacheControl cacheControl,
            @Nullable RequestProperties defaultRequestProperties) {
        super(/* isNetwork= */ true);
        this.callFactory = Assertions.checkNotNull(callFactory);
        this.userAgent = userAgent;
        this.contentTypePredicate = contentTypePredicate;
        this.cacheControl = cacheControl;
        this.defaultRequestProperties = defaultRequestProperties;
        this.requestProperties = new RequestProperties();
    }

    @Override
    public @Nullable
    Uri getUri() {
        return response == null ? null : Uri.parse(response.request().url().toString());
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return response == null ? Collections.emptyMap() : response.headers().toMultimap();
    }

    @Override
    public void setRequestProperty(String name, String value) {
        Assertions.checkNotNull(name);
        Assertions.checkNotNull(value);
        requestProperties.set(name, value);
    }

    @Override
    public void clearRequestProperty(String name) {
        Assertions.checkNotNull(name);
        requestProperties.remove(name);
    }

    @Override
    public void clearAllRequestProperties() {
        requestProperties.clear();
    }

    @Override
    public long open(DataSpec dataSpec) throws HttpDataSourceException {
        this.dataSpec = dataSpec;
        this.bytesRead = 0;
        this.bytesSkipped = 0;
        transferInitializing(dataSpec);

//        Log.d("datasource---  ", dataSpec.uri.toString() + "   " + dataSpec.uri.getPath());

        Request request = makeRequest(dataSpec);
        Response response;
        ResponseBody responseBody;

        try {
            this.response = callFactory.newCall(request).execute();
            response = this.response;
            responseBody = Assertions.checkNotNull(response.body());

            if (PlayerLibrary.isLocal == 1 && dataSpec.uri.toString().contains("m3u8")) {
                responseByteStream = responseBody.byteStream();
            } else {
                try {
                    SecretKeySpec skeySpec = new SecretKeySpec(PlayerLibrary.playKey.getBytes(), KeyProperties.KEY_ALGORITHM_AES);
                    Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
                    cipher.init(Cipher.DECRYPT_MODE, skeySpec);
                    responseByteStream = new CipherInputStream(responseBody.byteStream(), cipher);
//                    String outFilePath = "/sdcard/123/" + MD5Utils.encode(dataSpec.uri.getPath()) + ".ts";
//                    File inFile = new File(outFilePath);
//                    FileOutputStream fos = new FileOutputStream(inFile);
//                    byte[] bytesArray = responseBody.bytes();
//                    fos.write(bytesArray);
//                    fos.flush();
//                    fos.close();
//                    Log.d("datasource---: ", outFilePath);
//                    if (doFile(inFile, outFilePath)) {
//                        responseByteStream = new FileInputStream(outFilePath);
//                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            throw new HttpDataSourceException(
                    "Unable to connect to " + dataSpec.uri, e, dataSpec, HttpDataSourceException.TYPE_OPEN);
        }

        int responseCode = response.code();

        // Check for a valid response code.
        if (!response.isSuccessful()) {
            Map<String, List<String>> headers = response.headers().toMultimap();
            closeConnectionQuietly();
            InvalidResponseCodeException exception =
                    new InvalidResponseCodeException(responseCode, response.message(), headers, dataSpec);
            if (responseCode == 416) {
                exception.initCause(new DataSourceException(DataSourceException.POSITION_OUT_OF_RANGE));
            }
            throw exception;
        }

        // Check for a valid content type.
        MediaType mediaType = responseBody.contentType();
        String contentType = mediaType != null ? mediaType.toString() : "";
        if (contentTypePredicate != null && !contentTypePredicate.evaluate(contentType)) {
            closeConnectionQuietly();
            throw new InvalidContentTypeException(contentType, dataSpec);
        }

        // If we requested a range starting from a non-zero position and received a 200 rather than a
        // 206, then the server does not support partial requests. We'll need to manually skip to the
        // requested position.
        bytesToSkip = responseCode == 200 && dataSpec.position != 0 ? dataSpec.position : 0;

        // Determine the length of the data to be read, after skipping.
        if (dataSpec.length != C.LENGTH_UNSET) {
            bytesToRead = dataSpec.length;
        } else {
            long contentLength = responseBody.contentLength();
            bytesToRead = contentLength != -1 ? (contentLength - bytesToSkip) : C.LENGTH_UNSET;
        }

        opened = true;
        transferStarted(dataSpec);

        return bytesToRead;
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws HttpDataSourceException {
        try {
//            skipInternal();
            if (responseByteStream != null) {
                return responseByteStream.read(buffer, offset, readLength);
            } else {
                return 0;
            }
//            return readInternal(buffer, offset, readLength);
        } catch (IOException e) {
            throw new HttpDataSourceException(
                    e, Assertions.checkNotNull(dataSpec), HttpDataSourceException.TYPE_READ);
        }
    }

    @Override
    public void close() throws HttpDataSourceException {
        if (opened) {
            opened = false;
            transferEnded();
            closeConnectionQuietly();
        }
    }

    /**
     * Returns the number of bytes that have been skipped since the most recent call to
     * {@link #open(DataSpec)}.
     *
     * @return The number of bytes skipped.
     */
    protected final long bytesSkipped() {
        return bytesSkipped;
    }

    /**
     * Returns the number of bytes that have been read since the most recent call to
     * {@link #open(DataSpec)}.
     *
     * @return The number of bytes read.
     */
    protected final long bytesRead() {
        return bytesRead;
    }

    /**
     * Returns the number of bytes that are still to be read for the current {@link DataSpec}.
     * <p>
     * If the total length of the data being read is known, then this length minus {@code bytesRead()}
     * is returned. If the total length is unknown, {@link C#LENGTH_UNSET} is returned.
     *
     * @return The remaining length, or {@link C#LENGTH_UNSET}.
     */
    protected final long bytesRemaining() {
        return bytesToRead == C.LENGTH_UNSET ? bytesToRead : bytesToRead - bytesRead;
    }

    /**
     * Establishes a connection.
     */
    private Request makeRequest(DataSpec dataSpec) throws HttpDataSourceException {
        long position = dataSpec.position;
        long length = dataSpec.length;
        boolean allowGzip = dataSpec.isFlagSet(DataSpec.FLAG_ALLOW_GZIP);

        HttpUrl url = HttpUrl.parse(dataSpec.uri.toString());
        if (url == null) {
            throw new HttpDataSourceException(
                    "Malformed URL", dataSpec, HttpDataSourceException.TYPE_OPEN);
        }

        Request.Builder builder = new Request.Builder().url(url);
        if (cacheControl != null) {
            builder.cacheControl(cacheControl);
        }
        if (defaultRequestProperties != null) {
            for (Map.Entry<String, String> property : defaultRequestProperties.getSnapshot().entrySet()) {
                builder.header(property.getKey(), property.getValue());
            }
        }
        for (Map.Entry<String, String> property : requestProperties.getSnapshot().entrySet()) {
            builder.header(property.getKey(), property.getValue());
        }
        if (!(position == 0 && length == C.LENGTH_UNSET)) {
            String rangeRequest = "bytes=" + position + "-";
            if (length != C.LENGTH_UNSET) {
                rangeRequest += (position + length - 1);
            }
            builder.addHeader("Range", rangeRequest);
        }
        if (userAgent != null) {
            builder.addHeader("User-Agent", userAgent);
        }

        if (!allowGzip) {
            builder.addHeader("Accept-Encoding", "identity");
        }
        RequestBody requestBody = null;
        if (dataSpec.httpBody != null) {
            requestBody = RequestBody.create(null, dataSpec.httpBody);
        } else if (dataSpec.httpMethod == DataSpec.HTTP_METHOD_POST) {
            // OkHttp requires a non-null body for POST requests.
            requestBody = RequestBody.create(null, Util.EMPTY_BYTE_ARRAY);
        }
        builder.method(dataSpec.getHttpMethodString(), requestBody);
        return builder.build();
    }

    /**
     * Skips any bytes that need skipping. Else does nothing.
     * <p>
     * This implementation is based roughly on {@code libcore.io.Streams.skipByReading()}.
     *
     * @throws InterruptedIOException If the thread is interrupted during the operation.
     * @throws EOFException           If the end of the input stream is reached before the bytes are skipped.
     */
    private void skipInternal() throws IOException {
        if (bytesSkipped == bytesToSkip) {
            return;
        }

        while (bytesSkipped != bytesToSkip) {
            int readLength = (int) Math.min(bytesToSkip - bytesSkipped, SKIP_BUFFER.length);
            int read = castNonNull(responseByteStream).read(SKIP_BUFFER, 0, readLength);
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedIOException();
            }
            if (read == -1) {
                throw new EOFException();
            }
            bytesSkipped += read;
            bytesTransferred(read);
        }
    }

    /**
     * Reads up to {@code length} bytes of data and stores them into {@code buffer}, starting at
     * index {@code offset}.
     * <p>
     * This method blocks until at least one byte of data can be read, the end of the opened range is
     * detected, or an exception is thrown.
     *
     * @param buffer     The buffer into which the read data should be stored.
     * @param offset     The start offset into {@code buffer} at which data should be written.
     * @param readLength The maximum number of bytes to read.
     * @return The number of bytes read, or {@link C#RESULT_END_OF_INPUT} if the end of the opened
     * range is reached.
     * @throws IOException If an error occurs reading from the source.
     */
    private int readInternal(byte[] buffer, int offset, int readLength) throws IOException {
        if (readLength == 0) {
            return 0;
        }
        if (bytesToRead != C.LENGTH_UNSET) {
            long bytesRemaining = bytesToRead - bytesRead;
            if (bytesRemaining == 0) {
                return C.RESULT_END_OF_INPUT;
            }
            readLength = (int) Math.min(readLength, bytesRemaining);
        }

        int read = castNonNull(responseByteStream).read(buffer, offset, readLength);
        if (read == -1) {
            if (bytesToRead != C.LENGTH_UNSET) {
                // End of stream reached having not read sufficient data.
                throw new EOFException();
            }
            return C.RESULT_END_OF_INPUT;
        }

        bytesRead += read;
        bytesTransferred(read);
        return read;
    }

    /**
     * Closes the current connection quietly, if there is one.
     */
    private void closeConnectionQuietly() {
        if (response != null) {
            Assertions.checkNotNull(response.body()).close();
            response = null;
        }
        responseByteStream = null;
    }

    public boolean doFile(File inf, String outFile) {
        boolean result = false;
        try {
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(inf));
            byte[] bytIn = new byte[(int) inf.length()];
            bis.read(bytIn);
            bis.close();
            byte[] raw = "saIZXc4yMvq0Iz56".getBytes();
            SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, skeySpec);
            // 写文件
            byte[] bytOut = cipher.doFinal(bytIn);
            File outfile = new File(outFile);
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outfile));
            bos.write(bytOut);
            bos.close();
            result = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

}