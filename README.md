# Rest Contract Tooling docker image

This repo contains the tooling used to validate OpenAPI schema contracts as well as generate documentation from the schema.  It publishes a gradle plugin that can be used to manage and publish
a REST contract repository.  It also publishes a docker image so that the gradle project can be built without gradle or java installed locally, if desired.  No tools should be necessary to manage
a contract repository other than a text editor or IDE, bash, and a docker setup.  When built, a project using these tools will publish its schema as a versioned maven dependency to our internal
maven repository.

# Tools

## Validation
Validation tooling is based on [spectral](https://meta.stoplight.io/docs/spectral/674b27b261c3c-overview) and [spectral OpenAPI](https://meta.stoplight.io/docs/spectral/4dec24461f3af-open-api-rules). 

## Docs
Documentation generation uses [openapi-generator](https://openapi-generator.tech/)'s [html2](https://openapi-generator.tech/docs/generators/html2/) generator.

# Outputs

For each version directory, multiple outputs will be generated and published.  JSON and YAML versions of the entire spec will be compiled into a single
file so that it can be referenced easily as a single file.  Also a `.tar.gz` file will be generated that contains the original schema files, documentation,
_and_ dependencies.

These artifacts are then published twice, under a normal semantic version scheme and under a date + githash scheme.  Artifacts are always published under
a groupId of `com.projectronin.rest.contract`.

Thus, a project with two version directories at version 1.0.0 and 2.0.0 will publish something like this:

```text
.m2/repository/com/projectronin/rest/contract/<contract-artifact-id>
├── 1.0.0
│   ├── <contract-artifact-id>-1.0.0.json
│   ├── <contract-artifact-id>-1.0.0.pom
│   ├── <contract-artifact-id>-1.0.0.tar.gz
│   └── <contract-artifact-id>-1.0.0.yaml
├── maven-metadata-local.xml
└── v1-20230103180011-d9333ee
│   ├── <contract-artifact-id>-v1-20230103180011-d9333ee.json
│   ├── <contract-artifact-id>-v1-20230103180011-d9333ee.pom
│   ├── <contract-artifact-id>-v1-20230103180011-d9333ee.tar.gz
│   └── <contract-artifact-id>-v1-20230103180011-d9333ee.yaml
├── 2.0.0
│   ├── <contract-artifact-id>-2.0.0.json
│   ├── <contract-artifact-id>-2.0.0.pom
│   ├── <contract-artifact-id>-2.0.0.tar.gz
│   └── <contract-artifact-id>-2.0.0.yaml
├── maven-metadata-local.xml
└── v2-20230103180011-d9333ee
    ├── <contract-artifact-id>-v2-20230103180011-d9333ee.json
    ├── <contract-artifact-id>-v2-20230103180011-d9333ee.pom
    ├── <contract-artifact-id>-v2-20230103180011-d9333ee.tar.gz
    └── <contract-artifact-id>-v2-20230103180011-d9333ee.yaml
```

# Usage

## Project setup

Create a project with the following structure:

```
├── .gitignore
├── build.gradle.kts
├── settings.gradle.kts
├── v1
│   ├── <api-artifact-name>.json
│   └── schemas
│       ├── <referenced-spec-1>.json
│       ├── <referenced-spec-2>.json
│       ├── ...
│       └── <referenced-spec-n>.json
├── v2
│   ├── <api-artifact-name>.json
│   └── schemas
│       ├── <referenced-spec-1>.json
│       ├── <referenced-spec-2>.json
│       ├── ...
│       └── <referenced-spec-n>.json
└── vN
    ├── <api-artifact-name>.json
    └── schemas
        ├── <referenced-spec-1>.json
        ├── <referenced-spec-2>.json
        ├── ...
        └── <referenced-spec-n>.json
```

In the version directories, put your OpenAPI V3 specification.  The `vN` directory should contain one file that matches your rest contract project name / artifact id.  You can organize
your schema inside that any way you'd like, but the openapi spec should have an `info.version` field that contains a valid semantic version (e.g. 1.0.0, 3.4.7) where the first digit
matches the version directory number.

The content of the configuration files should be similar to the following:

### .gitignore
```gitignore
docs/
build/
/.idea/
gen/
.gradle/
.dependencies/

!gradle/wrapper/gradle-wrapper.jar
```

### settings.gradle.kts
```kotlin
rootProject.name = "<your rest contract name>"

pluginManagement {
    repositories {
        maven {
            url = uri("https://repo.devops.projectronin.io/repository/maven-snapshots/")
            mavenContent {
                snapshotsOnly()
            }
        }
        maven {
            url = uri("https://repo.devops.projectronin.io/repository/maven-releases/")
            mavenContent {
                releasesOnly()
            }
        }
        maven {
            url = uri("https://repo.devops.projectronin.io/repository/maven-public/")
            mavenContent {
                releasesOnly()
            }
        }
        mavenLocal()
        gradlePluginPortal()
    }
}
```

### build.gradle.kts
```kotlin
node {
    download.set(true)
    version.set("18.12.1")
}

plugins {
    id("com.projectronin.rest.contract.support") version "<plugin version>"
}
```

### Dependencies

If your contract depends on _other_ contracts, you can reference them as project dependencies.  Make entries in your build.gradle.kts file like this:

```kotlin
val v1 by configurations.creating

dependencies {
    v1("<dependency group id>:<dependency artifact id>:<dependency version>")
}
```

When you run the commands described below, these dependencies will be downloaded and placed in the `vN/.dependencies` directory based on the version you declared in your build file.  If they
are archives, they'll be unzipped, and you can reference the dependent contracts, schemas, etc. directly by path from inside your OpenAPI schema.

## Running

In general, either run:

`gradle <COMMAND> <ARGUMENTS>`

or

`docker run --rm -it -v "$PWD:/app" -v "$HOME/.m2/repository:/home/ronin/host-repository" "docker-repo.devops.projectronin.io/ronin-contract-rest-tooling:<TAG>" gradle <COMMAND> <ARGUMENTS>`

Available commands are mostly general gradle commands.  Important ones are:

`incrementApiVersion [-Psnapshot=true|false] [-Pversion-increment=MINOR|PATCH|NONE]`: Increments the semantic versions of all API versions.  Optional
arguments determine if the tool puts a `-SNAPSHOT` on the end of thew version and what level of incrementing takes place.  Level `NONE` can be used to get
the tooling to remove an existing `-SNAPSHOT` suffix without making other changes.  The tool will _also_ make sure that the semantic version in each directory
matches the directory's major version number.

`check`: Verifies the contract using spectral tooling, making sure it's a valid contract.

`downloadApiDependencies`: downloads any API dependencies as specified in the `Dependencies` section above.

`build`: Runs `check`, generates schema documentation, and generates simple combined schema files.

`clean`: removes all generated files

`assemble`: Assembles the schema into deployable archives.

`publishToMavenLocal`: publishes all outputs to the local maven repo (e.g. `$HOME/.m2/repository`).  If you are using the docker image, it will try and
copy files in and out of the host's repository directory so they can be used for builds later on the host.

`publish`: publishes all outputs to the remote Ronin maven repository.
