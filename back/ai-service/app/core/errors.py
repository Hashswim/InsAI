class ProviderFailure(Exception):
    """Public errors must never include model payloads or provider exceptions."""
