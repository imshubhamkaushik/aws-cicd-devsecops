import React from "react";
import { BrowserRouter as Router, Routes, Route, Link } from "react-router-dom";
import Users from "./components/Users";
import Products from "./components/Products";

function App() {
  const [currentUser, setCurrentUser] = React.useState(null);

  return (
    <Router>
      <div style={{ textAlign: "center", padding: "20px" }}>
        <h1>🛒 Catalogix</h1>

        {currentUser && (
          <p>
            Logged in as: <strong>{currentUser.name}</strong>
          </p>
        )}
        
        <nav>
          <Link to="/users" style={{ margin: "10px" }}>
            Users
          </Link>
          <Link to="/products" style={{ margin: "10px" }}>
            Products
          </Link>
        </nav>

        <Routes>
          <Route
            path="/users"
            element={<Users onUserSelected={setCurrentUser} />}
          />
          <Route
            path="/products"
            element={<Products currentUser={currentUser} />}
          />
          <Route path="/" element={<h2>Welcome to Catalogix!</h2>} />
        </Routes>
      </div>
    </Router>
  );
}

export default App;
