def call(){
  try{
      buildNpm()
  } catch(err){
    println "[ERROR] : Error encountered while yarn build"
    error("Error encountered while yarn build")
  }
}
