
# Python 3   npm, Node.js, Maven, and Spring Boot  Shell Commands Cheat Sheet

## Virtual Environments

- `python3 -m venv myenv`: Create a new virtual environment.
- `source myenv/bin/activate`: Activate a virtual environment.
- `deactivate`: Deactivate the current virtual environment.

## Package Management

- `pip3 install package-name`: Install a Python package.
- `pip3 uninstall package-name`: Uninstall a Python package.
- `pip3 freeze > requirements.txt`: Save installed packages to requirements.txt.
- `pip3 install -r requirements.txt`: Install packages from requirements.txt.

## Running Scripts

- `python3 script.py`: Run a Python script.

## Miscellaneous

- `python3 --version`: Display Python version.
- `python3 -m http.server`: Start a simple HTTP server.

# npm Shell Commands Cheat Sheet

## Package Management

- `npm install package-name`: Install a package locally.
- `npm install -g package-name`: Install a package globally.
- `npm uninstall package-name`: Uninstall a package.
- `npm update package-name`: Update a package.
- `npm search package-name`: Search for a package.

## Project Management

- `npm init`: Initialize a new Node.js project.
- `npm install`: Install project dependencies.
- `npm run script-name`: Run a script defined in package.json.

## Miscellaneous

- `npm version`: Display npm version.
- `npm list`: List installed packages.
- `npm help`: Get help on npm commands.

# Node.js Shell Commands Cheat Sheet

## Running Scripts

- `node script.js`: Run a Node.js script.

## REPL (Read-Eval-Print Loop)

- `node`: Start the Node.js REPL.

# Maven Shell Commands Cheat Sheet

## Project Management

- `mvn clean`: Clean the project.
- `mvn compile`: Compile the project.
- `mvn test`: Run tests.
- `mvn package`: Package the project.
- `mvn install`: Install the project artifact to the local repository.

## Dependency Management

- `mvn dependency:tree`: Display project dependency tree.
- `mvn dependency:resolve`: Resolve project dependencies.
- `mvn dependency:copy-dependencies`: Copy project dependencies.

## Miscellaneous

- `mvn --version`: Display Maven version.
- `mvn help:help`: Get help on Maven commands.

# Spring Boot Shell Commands Cheat Sheet

## Project Management

- `./mvnw spring-boot:run`: Run a Spring Boot application.
- `./mvnw clean package`: Package a Spring Boot application.
- `./mvnw spring-boot:build-image`: Build a Docker image for a Spring Boot application.

## Testing

- `./mvnw test`: Run tests.

## Miscellaneous

- `./mvnw --version`: Display Maven Wrapper version.

