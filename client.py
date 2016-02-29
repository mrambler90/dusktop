import socket, sys

portNumber = 1050
for arg in sys.argv:
	try:
		arg = int(arg)
		if arg >= 0 and arg <= 65535:
			portNumber = arg
			break
	except ValueError:
		pass

s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
print portNumber ; exit(0)
s.connect(("127.0.0.1", portNumber))

while True:

	try:
		msg = raw_input("> ")
		if len(msg) == 0:
			break
		else:
			s.send(msg + "\n")
			print s.recv(1024)
	except:
		print "Client experienced error ", e.errno, ": ", e.strerror, "."
		exit(0)

print "Client connection terminated"
