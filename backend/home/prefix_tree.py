"""Utilities for building and querying prefix trees for search suggestions."""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Dict, List, Iterable, Any, Set, Tuple
import unicodedata


def _strip_accents(value: str) -> str:
    """Return a lowercase string with accents removed and extra spaces collapsed."""
    normalized = unicodedata.normalize("NFD", value.lower())
    without_accents = "".join(ch for ch in normalized if unicodedata.category(ch) != "Mn")
    cleaned = "".join(ch if ch.isalnum() or ch.isspace() else " " for ch in without_accents)
    collapsed = " ".join(cleaned.split())
    return collapsed.strip()


def normalize_term(value: str) -> str:
    """Normalize text for prefix comparisons."""
    if not value:
        return ""
    return _strip_accents(value)


def term_variants(value: str) -> Set[str]:
    """Generate normalized variants (full name + individual tokens) for indexing."""
    normalized = normalize_term(value)
    if not normalized:
        return set()

    variants: Set[str] = {normalized}
    tokens = normalized.split()
    for token in tokens:
        if len(token) >= 2:
            variants.add(token)
    return variants


@dataclass
class TrieNode:
    children: Dict[str, "TrieNode"] = field(default_factory=dict)
    payloads: List[Dict[str, Any]] = field(default_factory=list)


class PrefixTree:
    """A simple prefix tree (trie) that stores payloads at terminal nodes."""

    def __init__(self) -> None:
        self.root = TrieNode()

    def insert(self, term: str, payload: Dict[str, Any]) -> None:
        """Insert a payload for the provided term."""
        key = normalize_term(term)
        if not key:
            return

        node = self.root
        for char in key:
            node = node.children.setdefault(char, TrieNode())
        node.payloads.append(payload)

    def suggest(
        self,
        prefix: str,
        limit: int = 10,
        item_type: str | None = None,
    ) -> List[Dict[str, Any]]:
        """Return payloads that match the prefix and optional place type."""
        key = normalize_term(prefix)
        if not key:
            return []

        normalized_type = item_type.upper() if item_type else None

        node = self.root
        for char in key:
            next_node = node.children.get(char)
            if next_node is None:
                return []
            node = next_node

        results: List[Dict[str, Any]] = []
        seen: Set[Tuple[Any, Any]] = set()

        def dfs(current: TrieNode) -> None:
            if len(results) >= limit:
                return

            for payload in current.payloads:
                if normalized_type and str(payload.get("type", "")).upper() != normalized_type:
                    continue
                identifier = (payload.get("type"), payload.get("id"))
                if identifier in seen:
                    continue
                seen.add(identifier)
                results.append(payload)
                if len(results) >= limit:
                    return

            for key_char in sorted(current.children.keys()):
                if len(results) >= limit:
                    break
                dfs(current.children[key_char])

        dfs(node)
        return results[:limit]


__all__ = ["PrefixTree", "term_variants", "normalize_term"]
