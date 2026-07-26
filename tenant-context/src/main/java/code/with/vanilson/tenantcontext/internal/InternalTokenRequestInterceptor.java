package code.with.vanilson.tenantcontext.internal;

import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * InternalTokenRequestInterceptor — outbound half of the service-to-service pattern.
 * <p>
 * Attaches the shared secret ({@code X-Internal-Token}) and this service's identity
 * ({@code X-Internal-Caller}) to a Feign call. It is the mirror of
 * {@link InternalTokenAuthenticationFilter} and lives in the same shared library so both ends of the
 * contract change together — the previous arrangement had the two halves hand-written in two
 * different services, which is exactly how a header name drifts and a call starts 401-ing.
 * <p>
 * <strong>Not a global bean.</strong> Register it on a specific client via
 * {@code @FeignClient(configuration = …)}. If it were picked up globally, every Feign client in the
 * service would start leaking the internal secret to whatever it talks to.
 * <p>
 * Fail-closed: a blank token means the header is simply not sent, so the callee rejects the call with
 * 401 rather than the caller silently sending an empty credential.
 *
 * @author vamuhong
 * @version 1.0
 */
public class InternalTokenRequestInterceptor implements RequestInterceptor {

    private final String token;
    private final String caller;

    public InternalTokenRequestInterceptor(String token, String caller) {
        this.token = token;
        this.caller = caller;
    }

    @Override
    public void apply(RequestTemplate template) {
        if (token != null && !token.isBlank()) {
            template.header(InternalTokenAuthenticationFilter.TOKEN_HEADER, token);
        }
        // Identity is optional on the wire: a callee with require-caller-header=false still accepts
        // the call and logs it as unidentified, which keeps a mid-upgrade cluster working.
        if (caller != null && !caller.isBlank()) {
            template.header(InternalTokenAuthenticationFilter.CALLER_HEADER, caller);
        }
    }
}
