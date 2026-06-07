The `annotations` module provides the declarative API for the **Notify.ai** client SDK. It contains Java annotations used by upstream applications to mark events, supply vocabulary definitions, configure rules, and manage callbacks without boilerplate code.

## 🛠️ Key Annotations

| Annotation | Target | Purpose |
| :--- | :--- | :--- |
| `@EnableNotify` | Class | Enables the Notify client SDK in the target application; triggers base package scanning. |
| `@Event` | Method | Intercepts execution of the annotated method, capturing parameters and payload to send to `acp-server`. |
| `@Rule` | Method | Declares a rule executor mapping to a specific event name. |
| `@Callback` | Method | Configures hooks (BEFORE/AFTER) to run logic in relation to event processing. |
| `@Vocabulary` | Field | Declares a field as a vocabulary attribute on a model class. |
| `@Model` | Class | Treats all fields of the class as vocabulary attributes. |
| `@VocabularySupplier` | Method | Supplies event payloads/context mappings. |
| `@SubjectSupplier` | Method | Supplies target recipients/subjects for a notification. |
| `@ManagedConfiguration` | Field | Declares a field that can be dynamically updated from ACP configurations. |

## 🚀 Local Compilation

Since this is a library containing pure Java annotations, it cannot be run as an independent application. To compile and install it to your local Maven repository:

```bash
# Build the annotations module
mvn clean install -pl annotations
```