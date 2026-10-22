from importlib.metadata import PackageNotFoundError, version

SERVICE_NAME = "insai-ai-service"


def package_version() -> str:
    try:
        return version(SERVICE_NAME)
    except PackageNotFoundError:
        return "0+unknown"


SERVICE_VERSION = package_version()
