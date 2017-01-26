package peergos.shared.user;

import jsinterop.annotations.*;
import peergos.shared.cbor.*;
import peergos.shared.corenode.*;
import peergos.shared.crypto.*;
import peergos.shared.crypto.asymmetric.*;
import peergos.shared.crypto.symmetric.*;
import peergos.shared.user.fs.*;
import peergos.shared.util.*;

import java.io.*;
import java.util.*;
import java.util.stream.*;

@JsType
public class EntryPoint implements Cborable{

    public final FilePointer pointer;
    public final String owner;
    public final Set<String> readers, writers;

    public EntryPoint(FilePointer pointer, String owner, Set<String> readers, Set<String> writers) {
        this.pointer = pointer;
        this.owner = owner;
        this.readers = readers;
        this.writers = writers;
    }

    public byte[] serializeAndEncrypt(BoxingKeyPair user, PublicBoxingKey target) throws IOException {
        return target.encryptMessageFor(this.serialize(), user.secretBoxingKey);
    }

    public byte[] serializeAndSymmetricallyEncrypt(SymmetricKey key) {
        byte[] nonce = key.createNonce();
        return ArrayOps.concat(nonce, key.encrypt(serialize(), nonce));
    }

    @Override
    public CborObject toCbor() {
        return new CborObject.CborList(Arrays.asList(
                pointer.toCbor(),
                new CborObject.CborString(owner),
                new CborObject.CborList(readers.stream().sorted().map(CborObject.CborString::new).collect(Collectors.toList())),
                new CborObject.CborList(writers.stream().sorted().map(CborObject.CborString::new).collect(Collectors.toList()))
        ));
    }

    static EntryPoint fromCbor(CborObject cbor) {
        if (! (cbor instanceof CborObject.CborList))
            throw new IllegalStateException("Incorrect cbor type for EntryPoint: " + cbor);

        List<CborObject> value = ((CborObject.CborList) cbor).value;
        FilePointer pointer = FilePointer.fromCbor(value.get(0));
        String owner = ((CborObject.CborString) value.get(1)).value;
        Set<String> readers = ((CborObject.CborList) value.get(2)).value
                .stream()
                .map(c -> ((CborObject.CborString) c).value)
                .collect(Collectors.toSet());
        Set<String> writers = ((CborObject.CborList) value.get(3)).value
                .stream()
                .map(c -> ((CborObject.CborString) c).value)
                .collect(Collectors.toSet());
        return new EntryPoint(pointer, owner, readers, writers);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        EntryPoint that = (EntryPoint) o;

        if (pointer != null ? !pointer.equals(that.pointer) : that.pointer != null) return false;
        if (owner != null ? !owner.equals(that.owner) : that.owner != null) return false;
        if (readers != null ? !readers.equals(that.readers) : that.readers != null) return false;
        return writers != null ? writers.equals(that.writers) : that.writers == null;

    }

    @Override
    public int hashCode() {
        int result = pointer != null ? pointer.hashCode() : 0;
        result = 31 * result + (owner != null ? owner.hashCode() : 0);
        result = 31 * result + (readers != null ? readers.hashCode() : 0);
        result = 31 * result + (writers != null ? writers.hashCode() : 0);
        return result;
    }

    static EntryPoint symmetricallyDecryptAndDeserialize(byte[] input, SymmetricKey key) throws IOException {
        byte[] nonce = Arrays.copyOfRange(input, 0, 24);
        byte[] raw = key.decrypt(Arrays.copyOfRange(input, 24, input.length), nonce);
        DataInputStream din = new DataInputStream(new ByteArrayInputStream(raw));
        FilePointer pointer = FilePointer.fromByteArray(Serialize.deserializeByteArray(din, 4*1024*1024));
        String owner = Serialize.deserializeString(din, CoreNode.MAX_USERNAME_SIZE);
        int nReaders = din.readInt();
        Set<String> readers = new HashSet<>();
        for (int i=0; i < nReaders; i++)
            readers.add(Serialize.deserializeString(din, CoreNode.MAX_USERNAME_SIZE));
        int nWriters = din.readInt();
        Set<String> writers = new HashSet<>();
        for (int i=0; i < nWriters; i++)
            writers.add(Serialize.deserializeString(din, CoreNode.MAX_USERNAME_SIZE));
        return new EntryPoint(pointer, owner, readers, writers);
    }

}
