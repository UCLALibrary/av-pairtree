## Documentation

Add documentation here.

### Sequence diagram

To generate the sequence diagrams from the mermaid source:

1. Install mermaid-cli:

    ```bash
    npm install @mermaid-js/mermaid-cli
    ```

2. Run:

    ```bash
    for diagram in sequence sequence_audio sequence_video
    do
        ./node_modules/.bin/mmdc -i docs/images/${diagram}.mermaid -o docs/images/${diagram}.svg
    done
    ```
