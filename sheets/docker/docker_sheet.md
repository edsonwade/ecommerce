# Docker , including Docker Compose, networks, volumes Commands Cheat Sheet

## Docker Basics

- `docker pull image_name:tag`: Pull an image from a registry.
- `docker images`: List all available images.
- `docker ps`: List running containers.
- `docker ps -a`: List all containers, including stopped ones.
- `docker run image_name:tag`: Create and start a new container.
- `docker start container_id`: Start a stopped container.
- `docker stop container_id`: Stop a running container.
- `docker rm container_id`: Remove a container.
- `docker rmi image_id`: Remove an image.
- `docker exec -it container_id /bin/bash`: Start an interactive shell inside a running container.

## Docker Compose

- `docker-compose up`: Start services defined in a `docker-compose.yml` file.
- `docker-compose down`: Stop and remove containers, networks, and volumes defined in a `docker-compose.yml` file.
- `docker-compose build`: Build or rebuild services defined in a `docker-compose.yml` file.
- `docker-compose restart service_name`: Restart a specific service.
- `docker-compose logs service_name`: View logs for a specific service.
- `docker-compose exec service_name command`: Execute a command in a running container.

## Docker Networks

- `docker network ls`: List Docker networks.
- `docker network create network_name`: Create a new Docker network.
- `docker network inspect network_name`: Inspect a Docker network.
- `docker network connect network_name container_name`: Connect a container to a network.
- `docker network disconnect network_name container_name`: Disconnect a container from a network.

## Docker Volumes

- `docker volume ls`: List Docker volumes.
- `docker volume create volume_name`: Create a new Docker volume.
- `docker volume inspect volume_name`: Inspect a Docker volume.
- `docker volume rm volume_name`: Remove a Docker volume.
- `docker volume prune`: Remove all unused Docker volumes.

## Docker Registry

- `docker login`: Log in to a Docker registry.
- `docker logout`: Log out from a Docker registry.
- `docker push image_name:tag`: Push an image to a Docker registry.

## Dockerfile

- `COPY source destination`: Copy files or directories from the host to the container.
- `RUN command`: Execute a command during the image build process.
- `CMD command`: Specify the default command to run when a container starts.
- `ENTRYPOINT command`: Specify the executable that will run when the container starts.
- `EXPOSE port`: Expose a port on which the container listens for connections.
- `WORKDIR path`: Set the working directory for any RUN, CMD, ENTRYPOINT, COPY, and ADD instructions.

## Docker Swarm

- `docker swarm init`: Initialize a Docker Swarm.
- `docker swarm join --token token manager_ip:port`: Join a Docker Swarm as a worker or manager.
- `docker service create --name service_name image_name:tag`: Deploy a new service to the Swarm.
- `docker service ls`: List services running in the Swarm.
- `docker service scale service_name=replica_count`: Scale a service to the specified number of replicas.
- `docker service update --replicas new_replica_count service_name`: Update the number of replicas for a service.

## docker
- `` docker container prune -f``: Remove all stopped containers

## clean up these orphan containers
-``docker-compose down --remove-orphans``: clean up these orphan containers

## delete all images
- `` docker rmi $(docker images -q) -f ``: delete all images

## list all container
 - docker container ps -a
## delete container one or more
- docker container rm  <can_add_here_the_containers-the two first letter or number>
