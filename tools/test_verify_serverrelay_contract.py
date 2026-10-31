import tempfile
import unittest
from pathlib import Path
from verify_serverrelay_contract import openapi_routes, retrofit_routes


class ContractVerifierTests(unittest.TestCase):
    def test_parses_paths_but_not_other_yaml_sections(self):
        spec = 'openapi: 3.1.0\npaths:\n  /api/v1/foo:\n    get:\n      responses: {}\n    post:\n      responses: {}\ncomponents:\n  schemas: {}'
        self.assertEqual({('GET', '/api/v1/foo'), ('POST', '/api/v1/foo')}, openapi_routes(spec))

    def test_extracts_retrofit_route_with_path_variable(self):
        with tempfile.TemporaryDirectory() as d:
            Path(d, 'Sample.kt').write_text('@GET("/api/v1/rooms/{roomId}")\nsuspend fun get()')
            self.assertEqual({('GET','/api/v1/rooms/{roomId}')}, retrofit_routes(Path(d)))


if __name__ == '__main__':
    unittest.main()
