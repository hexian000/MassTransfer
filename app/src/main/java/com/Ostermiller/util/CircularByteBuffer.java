/*
 * Circular Byte Buffer
 * Copyright (C) 2002-2010 Stephen Ostermiller
 * http://ostermiller.org/contact.pl?regarding=Java+Utilities
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * See LICENSE.txt for details.
 *
 * Modified by hexian000 for use in MassTransfer
 *
 */
package com.Ostermiller.util;

import android.support.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Implements the Circular Buffer producer/consumer model for bytes.
 * More information about this class is available from <a target="_top" href=
 * "http://ostermiller.org/utils/CircularByteBuffer.html">ostermiller.org</a>.
 * <p>
 * Using this class is a simpler alternative to using a PipedInputStream
 * and a PipedOutputStream. PipedInputStreams and PipedOutputStreams don't support the
 * mark operation, don't allow you to control buffer sizes that they use,
 * and have a more complicated API that requires instantiating two
 * classes and connecting them.
 * <p>
 * This class is thread safe.
 *
 * @author Stephen Ostermiller http://ostermiller.org/contact.pl?regarding=Java+Utilities
 */
public class CircularByteBuffer {

	/**
	 * The initial size for a circular byte buffer.
	 */
	private final static int INITIAL_SIZE = 64 * 1024;
	/**
	 * The default max capacity for a circular byte buffer.
	 */
	private final static int DEFAULT_CAPACITY = 1024 * 1024;

	/**
	 * The circular buffer.
	 * <p>
	 * The actual capacity of the buffer is one less than the actual length
	 * of the buffer so that an empty and a full buffer can be
	 * distinguished.  An empty buffer will have the markPostion and the
	 * writePosition equal to each other.  A full buffer will have
	 * the writePosition one less than the markPostion.
	 * <p>
	 * There are three important indexes into the buffer:
	 * The readPosition, the writePosition, and the markPosition.
	 * If the InputStream has never been marked, the readPosition and
	 * the markPosition should always be the same.  The bytes
	 * available to be read go from the readPosition to the writePosition,
	 * wrapping around the end of the buffer.  The space available for writing
	 * goes from the write position to one less than the markPosition,
	 * wrapping around the end of the buffer.  The bytes that have
	 * been saved to support a reset() of the InputStream go from markPosition
	 * to readPosition, wrapping around the end of the buffer.
	 */
	private byte[] buffer;
	/**
	 * Index of the first byte available to be read.
	 */
	private volatile int readPosition = 0;
	/**
	 * Max capacity can be resized to.
	 */
	private volatile int capacity = 0;
	/**
	 * Index of the first byte available to be written.
	 */
	private volatile int writePosition = 0;
	/**
	 * Index of the first saved byte. (To support stream marking.)
	 */
	private volatile int markPosition = 0;
	/**
	 * Number of bytes that have to be saved
	 * to support mark() and reset() on the InputStream.
	 */
	private volatile int markSize = 0;
	/**
	 * The InputStream that can empty this buffer.
	 */
	private InputStream in = new CircularByteBufferInputStream();
	/**
	 * true if the close() method has been called on the InputStream
	 */
	private boolean inputStreamClosed = false;
	/**
	 * The OutputStream that can fill this buffer.
	 */
	private OutputStream out = new CircularByteBufferOutputStream();
	/**
	 * true if the close() method has been called on the OutputStream
	 */
	private boolean outputStreamClosed = false;

	/**
	 * Create a new buffer with a default capacity.
	 * Writing to a full buffer will block until space
	 * is available rather than throw an exception.
	 */
	public CircularByteBuffer() {
		this(DEFAULT_CAPACITY);
	}

	/**
	 * Create a new buffer with given capacity.
	 * Writing to a full buffer will block until space
	 * is available rather than throw an exception.
	 * <p>
	 * Note that the buffer may reserve some bytes for
	 * special purposes and capacity number of bytes may
	 * not be able to be written to the buffer.
	 *
	 * @param size desired max capacity of the buffer in bytes.
	 */
	public CircularByteBuffer(int size) {
		buffer = new byte[INITIAL_SIZE];
		capacity = size;
	}

	/**
	 * Make this buffer ready for reuse.  The contents of the buffer
	 * will be cleared and the streams associated with this buffer
	 * will be reopened if they had been closed.
	 */
	public void clear() {
		synchronized (this) {
			readPosition = 0;
			writePosition = 0;
			markPosition = 0;
			outputStreamClosed = false;
			inputStreamClosed = false;
		}
	}

	/**
	 * Retrieve a OutputStream that can be used to fill
	 * this buffer.
	 * <p>
	 * Write methods may throw a BufferOverflowException if
	 * the buffer is not large enough.  A large enough buffer
	 * size must be chosen so that this does not happen or
	 * the caller must be prepared to catch the exception and
	 * try again once part of the buffer has been consumed.
	 *
	 * @return the producer for this buffer.
	 */
	public OutputStream getOutputStream() {
		return out;
	}

	/**
	 * Retrieve a InputStream that can be used to empty
	 * this buffer.
	 * <p>
	 * This InputStream supports marks at the expense
	 * of the buffer size.
	 *
	 * @return the consumer for this buffer.
	 */
	public InputStream getInputStream() {
		return in;
	}

	/**
	 * Get number of bytes that are available to be read.
	 * <p>
	 * Note that the number of bytes available plus
	 * the number of bytes free may not add up to the
	 * capacity of this buffer, as the buffer may reserve some
	 * space for other purposes.
	 *
	 * @return the size in bytes of this buffer
	 */
	public int getAvailable() {
		synchronized (this) {
			return available();
		}
	}

	/**
	 * Get the number of bytes this buffer has free for
	 * writing.
	 * <p>
	 * Note that the number of bytes available plus
	 * the number of bytes free may not add up to the
	 * capacity of this buffer, as the buffer may reserve some
	 * space for other purposes.
	 *
	 * @return the available space in bytes of this buffer
	 */
	public int getSpaceLeft() {
		synchronized (this) {
			return spaceLeft();
		}
	}

	/**
	 * Get the capacity of this buffer.
	 * <p>
	 * Note that the number of bytes available plus
	 * the number of bytes free may not add up to the
	 * capacity of this buffer, as the buffer may reserve some
	 * space for other purposes.
	 *
	 * @return the size in bytes of this buffer
	 */
	public int getSize() {
		synchronized (this) {
			return buffer.length;
		}
	}

	/**
	 * double the size of the buffer
	 */
	private void resize() {
		byte[] newBuffer = new byte[Math.min(buffer.length * 2, capacity)];
		int marked = marked();
		int available = available();
		if (markPosition <= writePosition) {
			// any space between the mark and
			// the first write needs to be saved.
			// In this case it is all in one piece.
			int length = writePosition - markPosition;
			System.arraycopy(buffer, markPosition, newBuffer, 0, length);
		} else {
			int length1 = buffer.length - markPosition;
			System.arraycopy(buffer, markPosition, newBuffer, 0, length1);
			int length2 = writePosition;
			System.arraycopy(buffer, 0, newBuffer, length1, length2);
		}
		buffer = newBuffer;
		markPosition = 0;
		readPosition = marked;
		writePosition = marked + available;
	}

	/**
	 * Space available in the buffer which can be written.
	 */
	private int spaceLeft() {
		if (writePosition < markPosition) {
			// any space between the first write and
			// the mark except one byte is available.
			// In this case it is all in one piece.
			return (markPosition - writePosition - 1);
		}
		// space at the beginning and end.
		return ((buffer.length - 1) - (writePosition - markPosition));
	}

	/**
	 * Bytes available for reading.
	 */
	private int available() {
		if (readPosition <= writePosition) {
			// any space between the first read and
			// the first write is available.  In this case i
			// is all in one piece.
			return (writePosition - readPosition);
		}
		// space at the beginning and end.
		return (buffer.length - (readPosition - writePosition));
	}

	/**
	 * Bytes saved for supporting marks.
	 */
	private int marked() {
		if (markPosition <= readPosition) {
			// any space between the markPosition and
			// the first write is marked.  In this case i
			// is all in one piece.
			return (readPosition - markPosition);
		}
		// space at the beginning and end.
		return (buffer.length - (markPosition - readPosition));
	}

	/**
	 * If we have passed the markSize reset the
	 * mark so that the space can be used.
	 */
	private void ensureMark() {
		if (marked() > markSize) {
			markPosition = readPosition;
			markSize = 0;
		}
	}

	/**
	 * Class for reading from a circular byte buffer.
	 */
	protected class CircularByteBufferInputStream extends InputStream {

		/**
		 * Returns the number of bytes that can be read (or skipped over) from this
		 * input stream without blocking by the next caller of a method for this input
		 * stream. The next caller might be the same thread or or another thread.
		 *
		 * @return the number of bytes that can be read from this input stream without blocking.
		 * @throws IOException if the stream is closed.
		 */
		@Override
		public int available() throws IOException {
			synchronized (CircularByteBuffer.this) {
				if (inputStreamClosed) {
					throw new IOException("InputStream has been closed, it is not ready.");
				}
				return (CircularByteBuffer.this.available());
			}
		}

		/**
		 * Close the stream. Once a stream has been closed, further read(), available(),
		 * mark(), or reset() invocations will throw an IOException. Closing a
		 * previously-closed stream, however, has no effect.
		 */
		@Override
		public void close() {
			synchronized (CircularByteBuffer.this) {
				inputStreamClosed = true;
			}
		}

		/**
		 * Mark the present position in the stream. Subsequent calls to reset() will
		 * attempt to reposition the stream to this point.
		 * <p>
		 * The readAheadLimit must be less than the size of circular buffer, otherwise
		 * this method has no effect.
		 *
		 * @param readAheadLimit Limit on the number of bytes that may be read while
		 *                       still preserving the mark. After reading this many bytes, attempting to
		 *                       reset the stream will fail.
		 */
		@Override
		public void mark(int readAheadLimit) {
			synchronized (CircularByteBuffer.this) {
				//if (inputStreamClosed) throw new IOException("InputStream has been closed; cannot mark a closed
				// InputStream.");
				if (buffer.length - 1 > readAheadLimit) {
					markSize = readAheadLimit;
					markPosition = readPosition;
				}
			}
		}

		/**
		 * Tell whether this stream supports the mark() operation.
		 *
		 * @return true, mark is supported.
		 */
		@Override
		public boolean markSupported() {
			return true;
		}

		/**
		 * Read a single byte.
		 * This method will block until a byte is available, an I/O error occurs,
		 * or the end of the stream is reached.
		 *
		 * @return The byte read, as an integer in the range 0 to 255 (0x00-0xff),
		 * or -1 if the end of the stream has been reached
		 * @throws IOException if the stream is closed.
		 */
		@Override
		public int read() throws IOException {
			while (true) {
				synchronized (CircularByteBuffer.this) {
					if (inputStreamClosed) {
						throw new IOException("InputStream has been closed; cannot read from a " +
								"closed InputStream.");
					}
					int available = CircularByteBuffer.this.available();
					if (available > 0) {
						int result = buffer[readPosition] & 0xff;
						readPosition++;
						if (readPosition == buffer.length) {
							readPosition = 0;
						}
						ensureMark();
						return result;
					} else if (outputStreamClosed) {
						return -1;
					}
				}
				try {
					Thread.sleep(100);
				} catch (Exception x) {
					throw new IOException("Blocking read operation interrupted.");
				}
			}
		}

		/**
		 * Read bytes into an array.
		 * This method will block until some input is available,
		 * an I/O error occurs, or the end of the stream is reached.
		 *
		 * @param b Destination buffer.
		 * @return The number of bytes read, or -1 if the end of
		 * the stream has been reached
		 * @throws IOException if the stream is closed.
		 */
		@Override
		public int read(@NonNull byte[] b) throws IOException {
			return read(b, 0, b.length);
		}

		/**
		 * Read bytes into a portion of an array.
		 * This method will block until some input is available,
		 * an I/O error occurs, or the end of the stream is reached.
		 *
		 * @param b   Destination buffer.
		 * @param off Offset at which to start storing bytes.
		 * @param len Maximum number of bytes to read.
		 * @return The number of bytes read, or -1 if the end of
		 * the stream has been reached
		 * @throws IOException if the stream is closed.
		 */
		@Override
		public int read(@NonNull byte[] b, int off, int len) throws IOException {
			while (true) {
				synchronized (CircularByteBuffer.this) {
					if (inputStreamClosed) {
						throw new IOException("InputStream has been closed; cannot read from a " +
								"closed InputStream.");
					}
					int available = CircularByteBuffer.this.available();
					if (available > 0) {
						int length = Math.min(len, available);
						int firstLen = Math.min(length, buffer.length - readPosition);
						int secondLen = length - firstLen;
						System.arraycopy(buffer, readPosition, b, off, firstLen);
						if (secondLen > 0) {
							System.arraycopy(buffer, 0, b, off + firstLen, secondLen);
							readPosition = secondLen;
						} else {
							readPosition += length;
						}
						if (readPosition == buffer.length) {
							readPosition = 0;
						}
						ensureMark();
						return length;
					} else if (outputStreamClosed) {
						return -1;
					}
				}
				try {
					Thread.sleep(100);
				} catch (Exception x) {
					throw new IOException("Blocking read operation interrupted.");
				}
			}
		}

		/**
		 * Reset the stream.
		 * If the stream has been marked, then attempt to reposition i
		 * at the mark. If the stream has not been marked, or more bytes
		 * than the readAheadLimit have been read, this method has no effect.
		 *
		 * @throws IOException if the stream is closed.
		 */
		@Override
		public void reset() throws IOException {
			synchronized (CircularByteBuffer.this) {
				if (inputStreamClosed) {
					throw new IOException("InputStream has been closed; cannot reset a closed InputStream.");
				}
				readPosition = markPosition;
			}
		}

		/**
		 * Skip bytes.
		 * This method will block until some bytes are available,
		 * an I/O error occurs, or the end of the stream is reached.
		 *
		 * @param n The number of bytes to skip
		 * @return The number of bytes actually skipped
		 * @throws IllegalArgumentException if n is negative.
		 * @throws IOException              if the stream is closed.
		 */
		@Override
		public long skip(long n) throws IOException, IllegalArgumentException {
			while (true) {
				synchronized (CircularByteBuffer.this) {
					if (inputStreamClosed) {
						throw new IOException("InputStream has been closed; cannot skip bytes on a " +
								"closed InputStream.");
					}
					int available = CircularByteBuffer.this.available();
					if (available > 0) {
						int length = Math.min((int) n, available);
						int firstLen = Math.min(length, buffer.length - readPosition);
						int secondLen = length - firstLen;
						if (secondLen > 0) {
							readPosition = secondLen;
						} else {
							readPosition += length;
						}
						if (readPosition == buffer.length) {
							readPosition = 0;
						}
						ensureMark();
						return length;
					} else if (outputStreamClosed) {
						return 0;
					}
				}
				try {
					Thread.sleep(100);
				} catch (Exception x) {
					throw new IOException("Blocking read operation interrupted.");
				}
			}
		}
	}

	/**
	 * Class for writing to a circular byte buffer.
	 * If the buffer is full, the writes will either block
	 * until there is some space available or throw an IOException
	 * based on the CircularByteBuffer's preference.
	 */
	protected class CircularByteBufferOutputStream extends OutputStream {

		/**
		 * Close the stream, flushing it first.
		 * This will cause the InputStream associated with this circular buffer
		 * to read its last bytes once it empties the buffer.
		 * Once a stream has been closed, further write() or flush() invocations
		 * will cause an IOException to be thrown. Closing a previously-closed stream,
		 * however, has no effect.
		 *
		 * @throws IOException never.
		 */
		@Override
		public void close() throws IOException {
			synchronized (CircularByteBuffer.this) {
				if (!outputStreamClosed) {
					flush();
				}
				outputStreamClosed = true;
			}
		}

		/**
		 * Flush the stream.
		 *
		 * @throws IOException if the stream is closed.
		 */
		@Override
		public void flush() throws IOException {
			synchronized (CircularByteBuffer.this) {
				if (outputStreamClosed) {
					throw new IOException("OutputStream has been closed; cannot flush a closed " +
							"OutputStream.");
				}
				if (inputStreamClosed) {
					throw new IOException("Buffer closed by inputStream; cannot flush.");
				}
			}
			// this method needs to do nothing
		}

		/**
		 * Write an array of bytes.
		 * If the buffer allows blocking writes, this method will block until
		 * all the data has been written rather than throw an IOException.
		 *
		 * @param b Array of bytes to be written
		 */
		@Override
		public void write(@NonNull byte[] b) throws IOException {
			write(b, 0, b.length);
		}

		/**
		 * Write a portion of an array of bytes.
		 * If the buffer allows blocking writes, this method will block until
		 * all the data has been written rather than throw an IOException.
		 *
		 * @param b   Array of bytes
		 * @param off Offset from which to start writing bytes
		 * @param len - Number of bytes to write
		 */
		@Override
		public void write(@NonNull byte[] b, int off, int len) throws IOException {
			while (len > 0) {
				synchronized (CircularByteBuffer.this) {
					if (outputStreamClosed) {
						throw new IOException("OutputStream has been closed; cannot write to a " +
								"closed OutputStream.");
					}
					if (inputStreamClosed) {
						throw new IOException("Buffer closed by InputStream; cannot write to a closed buffer.");
					}
					int spaceLeft = spaceLeft();
					while (spaceLeft < len && buffer.length < capacity) {
						resize();
						spaceLeft = spaceLeft();
					}
					int realLen = Math.min(len, spaceLeft);
					int firstLen = Math.min(realLen, buffer.length - writePosition);
					int secondLen = Math.min(realLen - firstLen, buffer.length - markPosition - 1);
					int written = firstLen + secondLen;
					if (firstLen > 0) {
						System.arraycopy(b, off, buffer, writePosition, firstLen);
					}
					if (secondLen > 0) {
						System.arraycopy(b, off + firstLen, buffer, 0, secondLen);
						writePosition = secondLen;
					} else {
						writePosition += written;
					}
					if (writePosition == buffer.length) {
						writePosition = 0;
					}
					off += written;
					len -= written;
				}
				if (len > 0) {
					try {
						Thread.sleep(100);
					} catch (Exception x) {
						throw new IOException("Waiting for available space in buffer interrupted.");
					}
				}
			}
		}

		/**
		 * Write a single byte.
		 * The byte to be written is contained in the 8 low-order bits of the
		 * given integer value; the 24 high-order bits are ignored.
		 * If the buffer allows blocking writes, this method will block until
		 * all the data has been written rather than throw an IOException.
		 *
		 * @param c number of bytes to be written
		 * @throws IOException if the stream is closed, or the write is interrupted.
		 */
		@Override
		public void write(int c) throws IOException {
			boolean written = false;
			while (!written) {
				synchronized (CircularByteBuffer.this) {
					if (outputStreamClosed) {
						throw new IOException("OutputStream has been closed; cannot write to a closed OutputStream.");
					}
					if (inputStreamClosed) {
						throw new IOException("Buffer closed by InputStream; cannot write to a closed buffer.");
					}
					int spaceLeft = spaceLeft();
					while (spaceLeft < 1 && buffer.length < capacity) {
						resize();
						spaceLeft = spaceLeft();
					}
					if (spaceLeft > 0) {
						buffer[writePosition] = (byte) (c & 0xff);
						writePosition++;
						if (writePosition == buffer.length) {
							writePosition = 0;
						}
						written = true;
					}
				}
				if (!written) {
					try {
						Thread.sleep(100);
					} catch (Exception x) {
						throw new IOException("Waiting for available space in buffer interrupted.");
					}
				}
			}
		}
	}
}