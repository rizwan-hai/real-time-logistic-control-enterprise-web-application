import unittest

from simulator import route_points


class RoutePointsTest(unittest.TestCase):
    def test_resamples_a_closed_route_in_order(self):
        points = route_points([(0, 0), (0, 10), (10, 10), (10, 0)], 8)

        self.assertEqual(points[0], (0, 0))
        self.assertEqual(points[2], (0, 10))
        self.assertEqual(points[4], (10, 10))
        self.assertEqual(points[6], (10, 0))

    def test_rejects_route_with_fewer_than_two_points(self):
        with self.assertRaises(ValueError):
            route_points([(0, 0)], 10)


if __name__ == "__main__":
    unittest.main()
