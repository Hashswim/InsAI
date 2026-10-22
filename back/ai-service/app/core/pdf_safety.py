"""Conservative admission for declared PDF actions/attachments, not malware scanning."""

from typing import Any
from urllib.parse import urlsplit

from pypdf import PdfReader
from pypdf.generic import ArrayObject, DictionaryObject, IndirectObject, NullObject

MAX_ACTIONS = 256
MAX_STRUCTURES = 1000
MAX_PAGE_ANNOTATIONS = 200


def _resolve(value: Any) -> Any:
    value = value.get_object() if isinstance(value, IndirectObject) else value
    return None if isinstance(value, NullObject) else value


def _dictionary(value: Any) -> DictionaryObject:
    value = _resolve(value)
    if not isinstance(value, DictionaryObject):
        raise ValueError("Unsupported PDF structure")
    return value


def _array(value: Any) -> ArrayObject:
    value = _resolve(value)
    if value is None:
        return ArrayObject()
    if not isinstance(value, ArrayObject) or len(value) > MAX_STRUCTURES:
        raise ValueError("Unsupported PDF structure")
    return value


def require_passive_pdf(reader: PdfReader) -> None:
    root = _dictionary(reader.trailer["/Root"])
    names = _resolve(root.get("/Names"))
    if names is not None:
        names = _dictionary(names)
        if "/JavaScript" in names or "/EmbeddedFiles" in names:
            raise ValueError("Unsupported active PDF feature")
    if "/Collection" in root:
        raise ValueError("Unsupported active PDF feature")
    actions: list[Any] = []

    def add_action(value: Any) -> None:
        value = _resolve(value)
        if value is not None:
            actions.append(value)
            if len(actions) > MAX_ACTIONS:
                raise ValueError("PDF action limit exceeded")

    opening = _resolve(root.get("/OpenAction"))
    if isinstance(opening, DictionaryObject):
        add_action(opening)
    elif opening is not None and not isinstance(opening, (ArrayObject, str)):
        raise ValueError("Unsupported PDF opening action")
    structures: list[Any] = [root]
    form = _resolve(root.get("/AcroForm"))
    if form is not None:
        form = _dictionary(form)
        if "/XFA" in form:
            raise ValueError("Unsupported active PDF feature")
        structures.extend(_array(form.get("/Fields")))
    for page in reader.pages:
        annotations = _array(page.get("/Annots"))
        if len(annotations) > MAX_PAGE_ANNOTATIONS:
            raise ValueError("PDF annotation limit exceeded")
        structures.extend((page, *annotations))
        if len(structures) > MAX_STRUCTURES:
            raise ValueError("PDF structure limit exceeded")
    seen_structures: set[int] = set()
    while structures:
        node = _dictionary(structures.pop())
        if id(node) in seen_structures:
            continue
        seen_structures.add(id(node))
        if len(seen_structures) > MAX_STRUCTURES:
            raise ValueError("PDF structure limit exceeded")
        if (
            "/AF" in node
            or "/RichMediaContent" in node
            or node.get("/Subtype")
            in {"/FileAttachment", "/RichMedia", "/3D", "/Movie", "/Sound", "/Screen"}
        ):
            raise ValueError("Unsupported active PDF feature")
        add_action(node.get("/A"))
        additional = _resolve(node.get("/AA"))
        if additional is not None:
            for action in _dictionary(additional).values():
                add_action(action)
        # Form field children can contain actions even when not visible annotations.
        if "/Kids" in node and node is not root:
            children = _array(node.get("/Kids"))
            if len(structures) + len(children) > MAX_STRUCTURES:
                raise ValueError("PDF structure limit exceeded")
            structures.extend(children)
    seen_actions: set[int] = set()
    while actions:
        action = _dictionary(actions.pop())
        if id(action) in seen_actions:
            continue
        seen_actions.add(id(action))
        if len(seen_actions) > MAX_ACTIONS:
            raise ValueError("PDF action limit exceeded")
        if action.get("/S") not in {"/GoTo", "/URI"}:
            raise ValueError("Unsupported PDF action")
        if action.get("/S") == "/URI":
            uri = _resolve(action.get("/URI"))
            if not isinstance(uri, str) or urlsplit(uri).scheme.lower() not in {
                "http",
                "https",
                "mailto",
                "tel",
            }:
                raise ValueError("Unsupported PDF URI")
        following = _resolve(action.get("/Next"))
        if isinstance(following, ArrayObject):
            if len(following) > MAX_ACTIONS:
                raise ValueError("PDF action limit exceeded")
            for action in following:
                add_action(action)
        else:
            add_action(following)
