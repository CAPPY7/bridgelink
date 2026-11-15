package app.bridgelink.android.protocol;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/** Why a link stopped. Fatal versus retry is the kind, never the wording. */
public final class LinkFailure extends IOException {
    public enum Kind {
        KEY_CHANGED(true),
        WRONG_CODE(true),
        TOKEN_REJECTED(true),
        VERSION_MISMATCH(true),
        TIMEOUT(false),
        REFUSED(false),
        NETWORK_LOST(false);

        public final boolean fatal;

        Kind(boolean fatal) {
            this.fatal = fatal;
        }
    }

    public final Kind kind;

    public LinkFailure(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public static Kind classify(Throwable error) {
        Throwable cursor = error;
        while (cursor != null) {
            if (cursor instanceof LinkFailure failure) return failure.kind;
            if (cursor instanceof SocketTimeoutException) return Kind.TIMEOUT;
            if (cursor instanceof ConnectException) return Kind.REFUSED;
            if (cursor instanceof UnknownHostException || cursor instanceof NoRouteToHostException) {
                return Kind.NETWORK_LOST;
            }
            if (cursor instanceof SocketException) return Kind.NETWORK_LOST;
            cursor = cursor.getCause();
        }
        return Kind.NETWORK_LOST;
    }

    /** What the person sees. The raw exception stays out of this sentence. */
    public static String message(Kind kind) {
        return switch (kind) {
            case KEY_CHANGED -> "This Mac key changed. Forget the Mac, then pair again.";
            case WRONG_CODE -> "The match code was wrong. Connect again.";
            case TOKEN_REJECTED -> "The Mac rejected this phone. Pair again.";
            case VERSION_MISMATCH -> "This phone and Mac need the same BridgeLink version.";
            case TIMEOUT -> "The Mac did not answer in time.";
            case REFUSED -> "The Mac refused the connection.";
            case NETWORK_LOST -> "The Wi-Fi link dropped.";
        };
    }

    public static Kind fromCode(String code) {
        if (code == null) return Kind.NETWORK_LOST;
        return switch (code) {
            case "key_changed" -> Kind.KEY_CHANGED;
            case "bad_proof", "bad_pin", "bad_code" -> Kind.WRONG_CODE;
            case "bad_token" -> Kind.TOKEN_REJECTED;
            case "bad_version" -> Kind.VERSION_MISMATCH;
            default -> Kind.NETWORK_LOST;
        };
    }
}
