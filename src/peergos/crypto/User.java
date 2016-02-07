package peergos.crypto;

import peergos.crypto.asymmetric.PublicBoxingKey;
import peergos.crypto.asymmetric.PublicSigningKey;
import peergos.crypto.asymmetric.SecretBoxingKey;
import peergos.crypto.asymmetric.SecretSigningKey;
import peergos.crypto.asymmetric.curve25519.Curve25519PublicKey;
import peergos.crypto.asymmetric.curve25519.Curve25519SecretKey;
import peergos.crypto.asymmetric.curve25519.Ed25519PublicKey;
import peergos.crypto.asymmetric.curve25519.Ed25519SecretKey;
import peergos.user.UserUtil;
import peergos.util.ArrayOps;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

public class User extends UserPublicKey
{
    public final SecretSigningKey secretSigningKey;
    public final SecretBoxingKey secretBoxingKey;

    public User(SecretSigningKey secretSigningKey, SecretBoxingKey secretBoxingKey, PublicSigningKey publicSigningKey, PublicBoxingKey publicBoxingKey)
    {
        super(publicSigningKey, publicBoxingKey);
        this.secretSigningKey = secretSigningKey;
        this.secretBoxingKey = secretBoxingKey;
    }

    public byte[] getSecretSigningKey() {
        return secretSigningKey.getSecretSigningKey();
    }

    public byte[] getSecretBoxingKey() {
        return secretBoxingKey.getSecretBoxingKey();
    }

    public byte[] signMessage(byte[] message)
    {
        return secretSigningKey.signMessage(message);
    }

    public byte[] decryptMessage(byte[] cipher, PublicBoxingKey theirPublicBoxingKey)
    {
        return secretBoxingKey.decryptMessage(cipher, theirPublicBoxingKey);
    }

    public static UserPublicKey deserialize(DataInputStream din) {
        try {
            boolean hasPrivateKeys = din.read() == 1;
            if (hasPrivateKeys) {
                SecretSigningKey signingKey = SecretSigningKey.deserialize(din);
                SecretBoxingKey boxingKey = SecretBoxingKey.deserialize(din);
                UserPublicKey pub = UserPublicKey.deserialize(din);
                return new User(signingKey, boxingKey, pub.publicSigningKey, pub.publicBoxingKey);
            }
            return UserPublicKey.deserialize(din);
        } catch (IOException e) {
            throw new IllegalStateException("Invalid serialized User", e);
        }
    }

    public byte[] serialize() {
        return ArrayOps.concat(secretSigningKey.serialize(), secretBoxingKey.serialize(), super.serialize());
    }

    public static User generateUserCredentials(String username, String password) {
        return UserUtil.generateUser(username, password).getUser();
    }


    public static User random() {

        byte[] secretSignBytes = new byte[64];
        byte[] publicSignBytes = new byte[32];
        byte[] secretBoxBytes = new byte[32];
        byte[] publicBoxBytes = new  byte[32];

        boolean isSeeded = false;
        TweetNaCl.crypto_sign_keypair(publicSignBytes, secretSignBytes, isSeeded);
        TweetNaCl.crypto_box_keypair(publicBoxBytes, secretBoxBytes, isSeeded);

        return new User(
                new Ed25519SecretKey(secretSignBytes),
                new Curve25519SecretKey(secretBoxBytes),
                new Ed25519PublicKey(publicSignBytes),
                new Curve25519PublicKey(publicBoxBytes));
    }

    public static void main(String[] args) {
        User user = User.generateUserCredentials("Username", "password");
        System.out.println("PublicKey: " + ArrayOps.bytesToHex(user.serialize()));
        byte[] message = "G'day mate!".getBytes();
        byte[] cipher = user.encryptMessageFor(message, user.secretBoxingKey);
        System.out.println("Cipher: "+ArrayOps.bytesToHex(cipher));
        byte[] clear = user.decryptMessage(cipher, user.publicBoxingKey);
        assert (Arrays.equals(message, clear));

        byte[] signed = user.signMessage(message);
        assert (Arrays.equals(user.unsignMessage(signed), message));
    }
}