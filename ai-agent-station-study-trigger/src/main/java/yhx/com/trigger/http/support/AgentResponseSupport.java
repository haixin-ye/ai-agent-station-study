package yhx.com.trigger.http.support;

import yhx.com.api.response.Response;

public class AgentResponseSupport {

    private AgentResponseSupport() {
    }

    public static <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code("0000")
                .info("success")
                .data(data)
                .build();
    }

    public static <T> Response<T> failed(String message) {
        return Response.<T>builder()
                .code("0001")
                .info(message == null || message.isBlank() ? "failed" : message)
                .build();
    }
}

