package space.dynomake.libretranslate;

import static java.nio.charset.StandardCharsets.UTF_8;
import static space.dynomake.libretranslate.ApiProviders.API_URL_FEDILAB;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import com.google.gson.Gson;

import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.UtilityClass;
import space.dynomake.libretranslate.exception.BadTranslatorResponseException;
import space.dynomake.libretranslate.type.LanguageTargets;
import space.dynomake.libretranslate.type.TranslateResponse;
import space.dynomake.libretranslate.util.JsonUtil;

@UtilityClass
public class Translator {
    @Setter
    private static String urlApi = API_URL_FEDILAB;

    @Setter
    private static String apiKey;

    @Setter
    private static int connectTimeout = 5000; // 5 seconds

    @Setter
    private static int readTimeout = 5000;

    public static void setUrlApi(String aKey) {
        urlApi = aKey;
    }

    public static void setApiKey(String aKey) {
        apiKey = aKey;
    }

    public static String translate(@NonNull String from, @NonNull String to, @NonNull String request) {
        return translateDetect(from, to, request).getTranslatedText();
    }

    public static TranslateResponse translateDetect(@NonNull String from, @NonNull String to, @NonNull String request) {
        return translateDetect(from, to, request, "text");
    }

    public static TranslateResponse translateDetect(@NonNull String from, @NonNull String to, @NonNull String
            request, @NonNull String format) {
        HttpURLConnection httpConn = null;
        try {
            URL url = new URL(urlApi);
            httpConn = (HttpURLConnection) url.openConnection();
            httpConn.setConnectTimeout(connectTimeout);
            httpConn.setReadTimeout(readTimeout);
            httpConn.setUseCaches(false);
            httpConn.setRequestMethod("POST");

            httpConn.setRequestProperty("Accept", "application/json");
            httpConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            httpConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");

            httpConn.setDoOutput(true);

            // Build request body
            String requestBody = "q=" + URLEncoder.encode(request, "UTF-8") + "&source=" + from + "&target=" + to + "&format=" + format;
            if (apiKey != null && !apiKey.isEmpty()) {
                requestBody += "&api_key=" + apiKey;
            }
            // Write request
            try (OutputStream outputStream = httpConn.getOutputStream();
                 OutputStreamWriter writer = new OutputStreamWriter(outputStream, UTF_8)) {
                writer.write(requestBody);
                writer.flush();
            }

            // Check response code before reading
            int responseCode = httpConn.getResponseCode();

            StringBuilder response = new StringBuilder();
            // Route to ErrorStream if the server responded with an error code
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            responseCode >= 400 ? httpConn.getErrorStream() : httpConn.getInputStream(),
                            StandardCharsets.UTF_8
                    )
            )) {
                String responseLine;
                while ((responseLine = reader.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                System.out.println("Response Payload:\n" + response);

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new BadTranslatorResponseException(responseCode, response.toString());
                }

                Gson gson = new Gson();
                return gson.fromJson(response.toString(), TranslateResponse.class);
                // return JsonUtil.from(reader, TranslateResponse.class); // not working
            }

            // try (InputStream responseStream = httpConn.getInputStream();
            //      BufferedReader reader = new BufferedReader(new InputStreamReader(responseStream, UTF_8))) {
            //    return JsonUtil.from(reader, TranslateResponse.class);
            // }
        }
        catch (IOException e) {
            throw new RuntimeException("Network error during translation: " + e.getMessage(), e);
        }
        catch (RuntimeException e) {
            throw e;
        }
        catch (Exception e) {
            throw new RuntimeException("Translation failed", e);
        }
        finally {
            if (httpConn != null) {
                httpConn.disconnect();
            }
        }
    }

    public String translate(@NonNull Language from, @NonNull Language to, @NonNull String request) {
        if (to == Language.NONE || from == to) return request;
        return translate(from.getCode(), to.getCode(), request);
    }

    public TranslateResponse translateDetect(@NonNull Language to, @NonNull String request) {
        return translateDetect("auto", to.getCode(), request);
    }

    public static String translate(@NonNull Language to, @NonNull String request) {
        if (to == Language.NONE) return request;
        return translate("auto", to.getCode(), request);
    }


    /**
     * Get supported languages for translation
     *
     * @param displayLanguage specify language for Name field. E.g. when "ru" then en lang Name will be "английский"
     *
     * @return an array of languages and target languages to which they can be translated
     */
    public LanguageTargets[] supportedLanguages(String displayLanguage) {
        HttpURLConnection httpConn = null;
        try {
            String languagesApi = getApiBaseUrl() + "/languages";
            URL url = new URL(languagesApi);
            httpConn = (HttpURLConnection) url.openConnection();
            httpConn.setConnectTimeout(connectTimeout);
            httpConn.setReadTimeout(readTimeout);
            httpConn.setUseCaches(false);
            httpConn.setRequestMethod("GET");

            if (displayLanguage != null) {
                httpConn.setRequestProperty("Accept-Language", displayLanguage);
            }
            httpConn.setRequestProperty("Accept", "application/json");
            httpConn.setRequestProperty("User-Agent", "Mozilla/5.0");


            // Check response code before reading
            int responseCode = httpConn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new BadTranslatorResponseException(responseCode, languagesApi);
            }

            try (InputStream responseStream = httpConn.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(responseStream, UTF_8))) {
                return JsonUtil.from(reader, LanguageTargets[].class);
            }
        }
        catch (IOException e) {
            throw new RuntimeException("Network error during translation", e);
        }
        catch (RuntimeException e) {
            throw e;
        }
        catch (Exception e) {
            throw new RuntimeException("Translation failed", e);
        }
        finally {
            if (httpConn != null) {
                httpConn.disconnect();
            }
        }
    }

    private String getApiBaseUrl() {
        return urlApi.substring(0, urlApi.length() - "/translate".length());
    }

}
