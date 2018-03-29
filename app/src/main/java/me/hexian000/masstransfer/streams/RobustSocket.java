package me.hexian000.masstransfer.streams;

class RobustSocket {
	RobustSocketInputStream getInputStream() {
		return new RobustSocketInputStream(this);
	}

	RobustSocketOutputStream getOutputStream() {
		return new RobustSocketOutputStream(this);
	}
}
