/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.luckperms.common.storage.wrappings;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;

import me.lucko.luckperms.common.buffers.Buffer;
import me.lucko.luckperms.common.model.Group;
import me.lucko.luckperms.common.model.Track;
import me.lucko.luckperms.common.model.User;
import me.lucko.luckperms.common.storage.Storage;

import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class BufferedOutputStorage implements Storage, Runnable {
    public static BufferedOutputStorage wrap(Storage storage, long flushTime) {
        return new BufferedOutputStorage(storage, flushTime);
    }

    @Getter
    @Delegate(excludes = Exclude.class)
    private final Storage backing;

    private final long flushTime;

    private final Buffer<User, Boolean> userOutputBuffer = Buffer.of(user -> BufferedOutputStorage.this.backing.saveUser(user).join());
    private final Buffer<Group, Boolean> groupOutputBuffer = Buffer.of(group -> BufferedOutputStorage.this.backing.saveGroup(group).join());
    private final Buffer<Track, Boolean> trackOutputBuffer = Buffer.of(track -> BufferedOutputStorage.this.backing.saveTrack(track).join());

    @Override
    public void run() {
        flush(flushTime);
    }

    public void forceFlush() {
        flush(-1);
    }

    public void flush(long flushTime) {
        userOutputBuffer.flush(flushTime);
        groupOutputBuffer.flush(flushTime);
        trackOutputBuffer.flush(flushTime);
    }

    @Override
    public Storage noBuffer() {
        return backing;
    }

    @Override
    public void shutdown() {
        forceFlush();
        backing.shutdown();
    }

    @Override
    public CompletableFuture<Boolean> saveUser(User user) {
        return userOutputBuffer.enqueue(user);
    }

    @Override
    public CompletableFuture<Boolean> saveGroup(Group group) {
        return groupOutputBuffer.enqueue(group);
    }

    @Override
    public CompletableFuture<Boolean> saveTrack(Track track) {
        return trackOutputBuffer.enqueue(track);
    }

    private interface Exclude {
        Storage noBuffer();
        CompletableFuture<Void> shutdown();
        CompletableFuture<Boolean> saveUser(User user);
        CompletableFuture<Boolean> saveGroup(Group group);
        CompletableFuture<Boolean> saveTrack(Track track);
    }
}
