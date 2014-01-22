#include <stdio.h>
#include <stdlib.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <netinet/in.h>
#include <errno.h>
#include <stdbool.h>

#define SERVER_PORT  1337
#define BUF_SIZE 1024
#define CONNECTIONS_ALLOWED 32
int const CONNECTION_TIMEOUT  = 2 * 60;

int err_exit(char* err, int socketDescriptor) {
	perror(err);
	close(socketDescriptor);
	exit(-1);
}

int main(int argc, char *argv[]) {

	// Not buffering output
	setbuf(stdout, NULL);


	int len, rc;
	int socketDescriptor, max_sd, new_sd;
	bool desc_ready, end_server = false, close_conn;
	char buffer[BUF_SIZE];
	struct sockaddr_in addr;
	struct fd_set master_set, working_set;

	// Create an AF_INET stream socket to receive incoming connections on
	socketDescriptor = socket(AF_INET, SOCK_STREAM, 0);
	if (socketDescriptor < 0) {
		perror("socket() failed");
		exit(-1);
	}

	// Allow socket descriptor to be reusable
	int optVal = 1;
	rc = setsockopt(socketDescriptor, SOL_SOCKET, SO_REUSEADDR, &optVal, sizeof(optVal));
	if (rc < 0) {
		err_exit("setsockopt() failed", socketDescriptor);
	}

	// Set socket to be nonblocking
	rc = ioctl(socketDescriptor, FIONBIO, &optVal);
	if (rc < 0) {
		err_exit("ioctl() failed", socketDescriptor);
	}

	// Bind the socket
	memset(&addr, 0, sizeof(addr));
	addr.sin_family = AF_INET;
	addr.sin_addr.s_addr = htonl(INADDR_ANY);
	addr.sin_port = htons(SERVER_PORT);
	rc = bind(socketDescriptor, (struct sockaddr *) &addr, sizeof(addr));
	if (rc < 0) {
		err_exit("bind() failed", socketDescriptor);
	}

	// Set the listen back log
	rc = listen(socketDescriptor, CONNECTIONS_ALLOWED);
	if (rc < 0) {
		err_exit("listen() failed", socketDescriptor);
	}

	FD_ZERO(&master_set);
	max_sd = socketDescriptor;
	FD_SET(socketDescriptor, &master_set);

	struct timeval timeout;
	timeout.tv_sec = CONNECTION_TIMEOUT;
	timeout.tv_usec = 0;

	printf("Server started.\n");
	while (!end_server) {
		/**********************************************************/
		/* Copy the master fd_set over to the working fd_set.     */
		/**********************************************************/
		memcpy(&working_set, &master_set, sizeof(master_set));
		//		working_set = master_set;

		printf("Waiting on select()...\n");
		rc = select(max_sd + 1, &working_set, NULL, NULL, &timeout);

		if (rc < 0) {
			perror("  select() failed");
			break;
		}
		if (rc == 0) {
			printf("  select() timed out.  End program.\n");
			break;
		}

		desc_ready = rc;
		int i;
		for (i = 0; i <= max_sd && desc_ready > 0; ++i) {
			if (FD_ISSET(i, &working_set)) {
				desc_ready -= 1;

				if (i == socketDescriptor) {
					printf("  Listening socket is readable\n");
					new_sd = accept(socketDescriptor, NULL, NULL);
					if (new_sd < 0) {
						perror("  accept() failed");
						end_server = true;
						break;
					}

					printf("  New incoming connection - %d\n", new_sd);
					FD_SET(new_sd, &master_set);
					if (new_sd > max_sd)
						max_sd = new_sd;
				} else {
					printf("  Descriptor %d is readable\n", i);
					close_conn = false;
					rc = recv(i, buffer, sizeof(buffer), 0);
					if (rc == 0) {
						printf("  Connection closed\n");
						close_conn = true;
					} else {
						len = rc;
						printf("  %d bytes received: ", len);
						char* readed = malloc(sizeof(char) * (len + 1));
						memcpy(&*readed, &buffer, len);
						readed[len] = 0;
						printf(readed);
						printf("\n");
						rc = send(i, buffer, len, 0);
						if (rc < 0) {
							perror("  send() failed");
							close_conn = true;
							break;
						}
					}

					if (close_conn) {
						close(i);
						FD_CLR(i, &master_set);
						if (i == max_sd) {
							while (FD_ISSET(max_sd, &master_set) == false)
								max_sd -= 1;
						}
					}
				}
			}
		}

	}

	// Clean up all of the sockets that are open
	int i;
	for (i = 0; i <= max_sd; ++i) {
		if (FD_ISSET(i, &master_set))
			close(i);
	}
}
