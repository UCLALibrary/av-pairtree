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
    ./node_modules/.bin/mmdc -i docs/images/architecture.mermaid -o docs/images/av_pairtree_sequence.svg
    ./node_modules/.bin/mmdc -i docs/images/av_pairtree_sequence_audio.mermaid -o docs/images/av_pairtree_sequence_audio.svg
    ```
