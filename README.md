# Rest Contract Tooling docker image

This docker image contains the tooling used to validate OpenAPI schema contracts as well as generate documentation from the schema. 

# Tools

## Validation
Validation tooling is based on [spectral](https://meta.stoplight.io/docs/spectral/674b27b261c3c-overview) and [spectral OpenAPI](https://meta.stoplight.io/docs/spectral/4dec24461f3af-open-api-rules). 

## Docs
Documentation generation uses [openapi-generator](https://openapi-generator.tech/)'s [html2](https://openapi-generator.tech/docs/generators/html2/) generator. 

# Usage
`docker run -it -v "<schema_root_directory>:/app" ronin-contract-rest-tooling:<tag> contract-tools [clean|test|doc]`

`clean`: Remove all generated files.

`test`: Test all versioned schemas against the curated examples. 

`docs`: Generate HTML documentation for each versioned schema.
