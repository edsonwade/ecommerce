# Linux Shell Commands Cheat Sheet

## File and Directory Operations

- `ls`: List directory contents.
- `ls -l`: List directory contents in long format.
- `ls -a`: List all files, including hidden files.
- `cd directory_name`: Change directory.
- `pwd`: Print working directory.
- `mkdir directory_name`: Create a new directory.
- `touch file_name`: Create a new empty file or update the access and modification times of the file.
- `cp source_file destination_file`: Copy files and directories.
- `mv source destination`: Move or rename files and directories.
- `rm file_name`: Remove files or directories.
- `cat file_name`: Display the contents of a file.
- `more file_name` or `less file_name`: View the contents of a file page by page.

## File Permissions

- `chmod permissions file_name`: Change file permissions.
- `chown user:group file_name`: Change file owner and group.
- `chgrp group file_name`: Change group ownership of a file.

## Searching and Filtering

- `grep pattern file_name`: Search for a pattern in a file.
- `grep -r pattern directory_name`: Recursively search for a pattern in files within a directory.
- `find directory_name -name file_name`: Search for files within a directory by name.

## Process Management

- `ps`: Display information about active processes.
- `ps aux`: Display detailed information about all processes.
- `kill process_id`: Terminate a process.
- `killall process_name`: Terminate all processes by name.

## System Information

- `uname -a`: Display system information.
- `hostname`: Display the system hostname.
- `df`: Display disk usage information.
- `du -h directory_name`: Display disk usage of a directory in human-readable format.
- `free -m`: Display memory usage information.

## Network Operations

- `ifconfig`: Display network interface configuration.
- `ping host`: Send ICMP echo request to a host.
- `netstat -tuln`: Display listening ports.
- `traceroute host`: Trace the route to a host.

## Compression and Archiving

- `tar options archive_name files_or_directories`: Create or extract tar archives.
- `gzip file_name`: Compress a file.
- `gunzip file_name`: Decompress a file.

## System Maintenance

- `sudo command`: Execute a command with superuser privileges.
- `shutdown`: Shutdown or reboot the system.
- `reboot`: Reboot the system.
- `top`: Display system resource usage and active processes.
- `htop`: Interactive process viewer.

## Text Processing

- `cat file_name | grep pattern`: Pipe output of one command to another.
- `wc file_name`: Count lines, words, and characters in a file.
- `head file_name`: Display the first few lines of a file.
- `tail file_name`: Display the last few lines of a file.
