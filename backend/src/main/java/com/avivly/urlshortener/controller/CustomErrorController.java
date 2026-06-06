package com.avivly.urlshortener.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

@Controller
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<?> handleError(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        String uri = String.valueOf(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI));

        boolean jsonRequest =
            accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE);

        Object statusAttr = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = statusAttr != null ? Integer.parseInt(statusAttr.toString()) : 500;
        HttpStatus httpStatus;
        try {
            httpStatus = HttpStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        if (jsonRequest) {
            return ResponseEntity.status(status).body(Map.of(
                "status",  status,
                "error",   httpStatus.getReasonPhrase(),
                "message", descriptionFor(status)
            ));
        }

        String statusText = httpStatus.getReasonPhrase();
        String description = descriptionFor(status);

        return ResponseEntity.status(status)
            .contentType(MediaType.TEXT_HTML)
            .body(htmlPage(status, statusText, description));
    }

    private String descriptionFor(int status) {
        return switch (status) {
            case 400 -> "The request was invalid or malformed.";
            case 403 -> "You don't have permission to access this resource.";
            case 404 -> "The page you're looking for doesn't exist.";
            case 405 -> "The HTTP method is not allowed for this endpoint.";
            case 500 -> "An unexpected error occurred on our end.";
            case 503 -> "The service is temporarily unavailable.";
            default  -> "Something went wrong. Please try again later.";
        };
    }

    private String iconFor(int status) {
        String d = switch (status) {
            case 400 -> "M9.75 9.75l4.5 4.5m0-4.5l-4.5 4.5M21 12a9 9 0 11-18 0 9 9 0 0118 0z";
            case 403 -> "M16.5 10.5V6.75a4.5 4.5 0 10-9 0v3.75m-.75 11.25h10.5a2.25 2.25 0 002.25-2.25v-6.75a2.25 2.25 0 00-2.25-2.25H6.75a2.25 2.25 0 00-2.25 2.25v6.75a2.25 2.25 0 002.25 2.25z";
            case 404 -> "M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607z";
            case 405 -> "M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728A9 9 0 015.636 5.636m12.728 12.728L5.636 5.636";
            case 500 -> "M11.42 15.17L17.25 21A2.652 2.652 0 0021 17.25l-5.877-5.877M11.42 15.17l2.496-3.03c.317-.384.74-.626 1.208-.766M11.42 15.17l-4.655 5.653a2.548 2.548 0 11-3.586-3.586l6.837-5.63m5.108-.233c.55-.164 1.163-.188 1.743-.14a4.5 4.5 0 004.486-6.336l-3.276 3.277a3.004 3.004 0 01-2.25-2.25l3.276-3.276a4.5 4.5 0 00-6.336 4.486c.091 1.076-.071 2.264-.904 2.95l-.102.085m-1.745 1.437L5.909 7.5H4.5L2.25 3.75l1.5-1.5L7.5 4.5v1.409l4.26 4.26m-1.745 1.437l1.745-1.437m6.615 8.206L15.75 15.75M4.867 19.125h.008v.008h-.008v-.008z";
            case 503 -> "M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z";
            default  -> "M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z";
        };
        return """
            <svg xmlns="http://www.w3.org/2000/svg" width="32" height="32"
                 fill="none" viewBox="0 0 24 24"
                 stroke="#2563eb" stroke-width="1.5"
                 stroke-linecap="round" stroke-linejoin="round">
              <path d="%s" />
            </svg>""".formatted(d);
    }

    private String htmlPage(int status, String statusText, String description) {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <title>%d %s</title>
              <style>
                *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
                body { display: flex; flex-direction: column; min-height: 100vh; background: #f9fafb; font-family: system-ui, sans-serif; }
                header { background: #2563eb; color: white; padding: 1rem 1.5rem; box-shadow: 0 1px 3px rgba(0,0,0,.2); }
                header h1 { font-size: 1.1rem; font-weight: 600; }
                main { flex: 1; display: flex; align-items: center; justify-content: center; padding: 2rem 1rem; }
                .card { background: white; border-radius: .75rem; box-shadow: 0 4px 6px -1px rgba(0,0,0,.07), 0 2px 4px -2px rgba(0,0,0,.05); max-width: 420px; width: 100%%; padding: 2.5rem 2rem; text-align: center; }
                .icon-wrap { display: inline-flex; width: 4rem; height: 4rem; border-radius: 50%%; background: #eff6ff; align-items: center; justify-content: center; margin-bottom: 1rem; }
                .code { font-size: 5rem; font-weight: 800; color: #2563eb; line-height: 1; }
                .title { font-size: 1.2rem; font-weight: 600; color: #111827; margin: .5rem 0; }
                .desc { font-size: 0.9rem; color: #6b7280; margin: 0 0 1.5rem; }
                .btn { display: inline-block; background: #2563eb; color: white; text-decoration: none; padding: .625rem 1.5rem; border-radius: .5rem; font-size: .9rem; font-weight: 500; }
                .btn:hover { background: #1d4ed8; }
                footer { text-align: center; font-size: .75rem; color: #9ca3af; padding: 1rem; }
                @media (max-width: 480px) {
                  .card { padding: 2rem 1.5rem; }
                  .code { font-size: 3.5rem; }
                }
              </style>
            </head>
            <body>
              <header><h1>URL Shortener</h1></header>
              <main>
                <div class="card">
                  <div class="icon-wrap">%s</div>
                  <div class="code">%d</div>
                  <p class="title">%s</p>
                  <p class="desc">%s</p>
                  <a href="/" class="btn">Back to Dashboard</a>
                </div>
              </main>
              <footer>Avivly URL Shortener</footer>
            </body>
            </html>
            """.formatted(status, statusText, iconFor(status), status, statusText, description);
    }
}
