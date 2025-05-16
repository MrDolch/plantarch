# PlantArch

[![mvn verify][ci_img]][ci_link]
[![Maven Central][maven_img]][maven_link]

**PlantArch** is a lightweight and easily configurable tool to generate architecture diagrams from Java classes —
inspired by [PlantUML](https://plantuml.com/) and software architecture principles.

## ✨ Features

- Parses Java classes and packages
- Supports **Class Diagrams**, **Entity-Relationship Models (ERMs)**, and **Sequence Diagrams**
- Automatically generates PlantUML-compatible output
- Easily configurable for different project structures
- Helps document and visualize software architecture

## 📦 Installation

Add the following dependency to your `pom.xml`:

```xml

<dependency>
    <groupId>io.github.mrdolch</groupId>
    <artifactId>plantarch</artifactId>
    <version>0.1.1</version>
</dependency>
```

Requires Java 8 or higher

## ⚙️ Diagram Types

- Class Diagram – shows classes and their relationships (inheritance, dependencies, etc.)
- ERM (Entity-Relationship Model) – useful for database schema visualization
- Sequence Diagram – illustrates object interactions over time

## 🚀 Usage

You can find a usage example in the test suite:
👉 [ClassDiagramTest.kt](src/test/kotlin/tech/dolch/plantarch/ClassDiagramTest.kt)

## Examples:

![Class Diagram of Class ClassDiagram](docs/ClassDiagram-Class_Diagram_of_ClassDiagram.png)

![Class Diagram of Class ErmDiagram](docs/ErmDiagram-Class_Diagram_of_ErmDiagram.png)

![Class Diagram of Class SequenceDiagram](docs/SequenceDiagram-Class_Diagram_of_SequenceDiagram.png)

![Sequence Diagram of Class SequenceDiagram](docs/SequenceDiagram-Sequence_Diagram_of_PlantArch.png)

![ERM Diagram of Example](docs/JPA%20Diagram-Erm_Diagram_of_PlantArch.png)

![Class Diagram of Example](docs/Test_ClassDiagram-Test_Class_Diagram_of_Car.png)

## 🤝 Contributing

Contributions are welcome! If you'd like to improve PlantArch, feel free to open a pull request or start a discussion
via issues.

[ci_img]: https://github.com/mrdolch/plantarch/actions/workflows/maven-verify.yml/badge.svg

[ci_link]: https://github.com/mrdolch/plantarch/actions/maven-verify.yml

[maven_img]: https://maven-badges.herokuapp.com/maven-central/io.github.mrdolch/plantarch/badge.svg

[maven_link]: https://maven-badges.herokuapp.com/maven-central/io.github.mrdolch/plantarch